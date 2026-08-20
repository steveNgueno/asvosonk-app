package org.asvosonk.session.application.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.asvosonk.cashbox.application.service.CashboxService;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;
import org.asvosonk.cashbox.domain.valueobject.MovementDirection;
import org.asvosonk.cashbox.domain.valueobject.MovementOrigin;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.repository.MemberRepository;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;
import org.asvosonk.member.infrastructure.persistence.repository.MembershipFeePaymentRepository;
import org.asvosonk.presence.application.usecase.MarkPresenceBenefitedUseCase;
import org.asvosonk.presence.domain.repository.PresenceTourRepository;
import org.asvosonk.sanction.application.usecase.DeductSanctionsUseCase;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.session.application.usecase.ComputePresenceFeeUseCase;
import org.asvosonk.session.application.usecase.GenerateSessionReportUseCase;
import org.asvosonk.session.domain.valueobject.SessionStatus;
import org.asvosonk.session.domain.valueobject.SessionStep;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.asvosonk.session.infrastructure.persistence.entity.SessionAttendanceEntity;
import org.asvosonk.session.infrastructure.persistence.entity.SessionReportEntity;
import org.asvosonk.session.infrastructure.persistence.repository.SessionReportRepository;
import org.asvosonk.tontine.domain.model.TontineContribution;
import org.asvosonk.tontine.domain.repository.TontineContributionRepository;
import org.asvosonk.tontine.domain.repository.TontineParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Déroulé d'une séance, étape par étape.
 *
 * <p>Chaque transition est idempotente : la séance est verrouillée en écriture et
 * l'appelant déclare l'étape sur laquelle il pense être, ce qui empêche un double
 * envoi de rejouer un traitement financier.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionStepService {

    private static final BigDecimal BEVERAGE_COST_PER_PERSON = new BigDecimal("500");

    private final SessionService                  sessionService;
    private final EntityManager                   entityManager;
    private final MemberRepository                memberRepository;
    private final RevolvingFundService            revolvingFundService;
    private final CashboxService                  cashboxService;
    private final SessionReportRepository         sessionReportRepository;
    private final PresenceTourRepository          presenceTourRepository;
    private final MarkPresenceBenefitedUseCase    markPresenceBenefitedUseCase;
    private final ComputePresenceFeeUseCase       computePresenceFeeUseCase;
    private final DeductSanctionsUseCase          deductSanctionsUseCase;
    private final GenerateSessionReportUseCase    generateSessionReportUseCase;
    private final TontineContributionRepository   tontineContributionRepository;
    private final TontineParticipantRepository    tontineParticipantRepository;
    private final MembershipFeePaymentRepository  membershipFeePaymentRepository;

    @Transactional
    public MeetingSessionEntity transitionToNext(Long sessionId, AppUser user) {
        return transitionToNext(sessionId, user, null);
    }

    /**
     * Fait avancer la séance d'une étape.
     *
     * <p>Protections contre le rejeu : la ligne de séance est verrouillée
     * (PESSIMISTIC_WRITE), une séance déjà au rapport est refusée, et si
     * {@code expectedCurrent} est fourni il doit correspondre à l'étape réelle.</p>
     */
    @Transactional
    public MeetingSessionEntity transitionToNext(Long sessionId, AppUser user, SessionStep expectedCurrent) {
        MeetingSessionEntity session = sessionService.findByIdForUpdate(sessionId);
        SessionStep current = session.getCurrentStepEnum();

        if (expectedCurrent != null && current != expectedCurrent) {
            throw new BusinessRuleException(
                "Cette étape a déjà été traitée (étape courante : " + current.label() + ").");
        }

        SessionStep next = current.next();
        if (next == null) {
            throw new BusinessRuleException("La séance a déjà atteint l'étape finale.");
        }

        executeTransition(session, next, user);
        session.setCurrentStepEnum(next);
        entityManager.merge(session);

        log.info("Séance {} : passage de {} à {}", session.getId(), current, next);
        return session;
    }

    private void executeTransition(MeetingSessionEntity session, SessionStep step, AppUser user) {
        switch (step) {
            case PRESENCE_OPEN          -> requireOpenPresenceTour();
            case PRESENCE_CLOSED        -> closePresence(session, user);
            case TONTINE_CLOSED         -> closeTontine(session, user);
            case BANQUE_PROJET_CLOSED   -> session.setBanqueProjetClosedAt(LocalDateTime.now());
            case BANQUE_ANNUELLE_CLOSED -> session.setBanqueAnnuelleClosedAt(LocalDateTime.now());
            case REPORT_GENERATED       -> generateReport(session, user);
            default                     -> { }
        }
    }

    // ── PRESENCE_OPEN ─────────────────────────────────────────

    /**
     * La présence ne peut s'ouvrir sans tour en cours : c'est lui qui porte la
     * rotation des bénéficiaires. Le bénéficiaire du jour, lui, est désigné par le
     * secrétaire (tirage au sort en séance) avant la clôture.
     */
    private void requireOpenPresenceTour() {
        if (presenceTourRepository.findCurrentOpenTour().isEmpty()) {
            throw new BusinessRuleException(
                "Aucun tour de présence n'est ouvert : créez-le avant d'ouvrir la saisie de présence.");
        }
    }

    // ── PRESENCE_CLOSED — traitement financier complet ────────

    private void closePresence(MeetingSessionEntity session, AppUser user) {
        Long sessionId = session.getId();
        MemberEntity beneficiaryEntity = session.getPresenceBeneficiary();
        if (beneficiaryEntity == null) {
            throw new BusinessRuleException(
                "Désignez le bénéficiaire de la tontine de présence avant de clôturer.");
        }

        var openTour = presenceTourRepository.findCurrentOpenTour().orElse(null);
        Map<Long, BigDecimal> fees = openTour != null
            ? computePresenceFeeUseCase.feesByMember(openTour.getId(), beneficiaryEntity.getId())
            : Map.of();

        List<SessionAttendanceEntity> attendances = entityManager.createQuery("""
                SELECT a FROM SessionAttendanceEntity a JOIN a.member m
                 WHERE a.session.id = :sessionId
                 ORDER BY m.fullName ASC
                """, SessionAttendanceEntity.class)
            .setParameter("sessionId", sessionId)
            .getResultList();

        BigDecimal totalTontine     = BigDecimal.ZERO;
        BigDecimal beveragePool     = BigDecimal.ZERO;
        BigDecimal totalDevelopment = BigDecimal.ZERO;
        BigDecimal returnToFund     = BigDecimal.ZERO;
        BigDecimal recoveryTotal    = BigDecimal.ZERO;
        int presentCount = 0, fundCoveredCount = 0, defaultCount = 0;
        List<RevolvingFundService.Recovery> recoveries = new ArrayList<>();

        // Le bénéficiaire est traité en dernier : la tontine des autres membres
        // doit être connue avant de pouvoir y prélever sa mise à jour.
        SessionAttendanceEntity beneficiaryAttendance = null;
        List<SessionAttendanceEntity> others = new ArrayList<>();
        for (SessionAttendanceEntity att : attendances) {
            if (att.getMember().getId().equals(beneficiaryEntity.getId())) {
                beneficiaryAttendance = att;
            } else {
                others.add(att);
            }
        }

        for (SessionAttendanceEntity att : others) {
            Member member = memberRepository.findById(att.getMember().getId()).orElse(null);
            if (member == null) continue;

            att.setAmountDue(computePresenceFeeUseCase.feeFor(fees, member.getId()));

            RevolvingFundService.AttendanceResult r =
                revolvingFundService.process(member, att.getAmountPaid(), session, att, user);

            totalTontine     = totalTontine.add(r.getContributionToTontine());
            beveragePool     = beveragePool.add(r.totalBeverage());
            totalDevelopment = totalDevelopment.add(r.totalDevelopment());
            returnToFund     = returnToFund.add(r.getReturnToFund());
            recoveries.addAll(r.getRecoveries());

            if (att.isPresent())     presentCount++;
            if (r.isCoveredByFund()) fundCoveredCount++;
            if (r.isDefault())       defaultCount++;
        }

        // ── Le bénéficiaire, régularisé d'office sur sa tontine ──────────
        BigDecimal fundCatchUp = BigDecimal.ZERO;
        if (beneficiaryAttendance != null) {
            Member member = memberRepository.findById(beneficiaryAttendance.getMember().getId())
                .orElse(null);
            if (member != null) {
                beneficiaryAttendance.setAmountDue(
                    computePresenceFeeUseCase.feeFor(fees, member.getId()));

                RevolvingFundService.AttendanceResult r = revolvingFundService.process(
                    member, beneficiaryAttendance.getAmountPaid(), session,
                    beneficiaryAttendance, user, totalTontine);

                fundCatchUp      = r.getFromTontine();
                totalTontine     = totalTontine.add(r.getContributionToTontine());
                beveragePool     = beveragePool.add(r.totalBeverage());
                totalDevelopment = totalDevelopment.add(r.totalDevelopment());
                returnToFund     = returnToFund.add(r.getReturnToFund());
                recoveries.addAll(r.getRecoveries());

                if (beneficiaryAttendance.isPresent()) presentCount++;
                if (r.isCoveredByFund()) fundCoveredCount++;
                if (r.isDefault())       defaultCount++;
            }
        }

        for (RevolvingFundService.Recovery recovery : recoveries) {
            recoveryTotal = recoveryTotal.add(recovery.amount());
        }

        // ── Boisson : on n'achète que pour les membres présents ──
        BigDecimal beverageCost = BEVERAGE_COST_PER_PERSON.multiply(BigDecimal.valueOf(presentCount));
        BigDecimal beverageReliquat = beveragePool.subtract(beverageCost).max(BigDecimal.ZERO);
        if (beverageReliquat.signum() > 0) {
            cashboxService.credit(CashboxType.beverage, beverageReliquat,
                "Reliquat boisson séance " + session.getSessionDate(),
                MovementOrigin.presence, session, null, null, user);
        }

        // ── Retenues sur la tontine du bénéficiaire ─────────────────────
        // La mise à jour du fond de roulement passe avant tout : elle est
        // obligatoire. Les sanctions impayées sont ensuite retenues sur ce qui
        // reste, avec imputation partielle.
        BigDecimal afterCatchUp = totalTontine.subtract(fundCatchUp).max(BigDecimal.ZERO);
        BigDecimal deductions = deductSanctionsUseCase.deduct(
            beneficiaryEntity.getId(), afterCatchUp, session,
            "Retenue sur tontine de présence — séance " + session.getSessionDate(), user);
        BigDecimal netTontine = afterCatchUp.subtract(deductions).max(BigDecimal.ZERO);

        // ── Le bénéficiaire est marqué comme servi dans le tour ──
        presenceTourRepository.findCurrentOpenTour().ifPresent(tour -> {
            try {
                markPresenceBenefitedUseCase.execute(tour.getId(), beneficiaryEntity.getId(), sessionId);
            } catch (RuntimeException e) {
                log.warn("Marquage du bénéficiaire de présence impossible : {}", e.getMessage());
            }
        });

        // ── Rapport de séance ────────────────────────────────
        SessionReportEntity report = sessionReportRepository.findBySessionId(sessionId)
            .orElseGet(SessionReportEntity::new);
        report.setSessionId(sessionId);
        report.setPresenceBeneficiaryId(beneficiaryEntity.getId());
        report.setPresenceTotalCotisants(attendances.size());
        report.setPresencePresentCount(presentCount);
        report.setPresenceFundCoveredCount(fundCoveredCount);
        report.setPresenceDefaultCount(defaultCount);
        report.setPresenceGrossTontine(totalTontine);
        report.setPresenceFundCatchUp(fundCatchUp);
        report.setPresenceSanctionDeductions(deductions);
        report.setPresenceNetTontine(netTontine);
        report.setPresenceDevelopmentTotal(totalDevelopment);
        report.setPresenceBeverageReliquat(beverageReliquat);
        report.setPresenceReturnToFund(returnToFund);
        report.setPresenceRecoveryTotal(recoveryTotal);
        refreshCashFlows(report, sessionId);
        sessionReportRepository.save(report);

        session.setPresenceClosedAt(LocalDateTime.now());

        log.info("Présence clôturée — séance {} : tontine nette={}, reliquat boisson={}, "
                 + "retour en caisse={}, rattrapages={}, échecs={}",
                 session.getSessionDate(), netTontine, beverageReliquat,
                 returnToFund, recoveryTotal, defaultCount);
    }

    // ── TONTINE_CLOSED ────────────────────────────────────────

    private void closeTontine(MeetingSessionEntity session, AppUser user) {
        Long sessionId = session.getId();

        List<TontineContribution> contributions =
            tontineContributionRepository.findBySessionId(sessionId);

        BigDecimal gross = BigDecimal.ZERO;
        Long beneficiaryId = null;
        int defaults = 0;
        for (TontineContribution c : contributions) {
            beneficiaryId = c.getBeneficiaryId();
            if (c.isPaid()) {
                gross = gross.add(c.getAmount());
            } else {
                defaults++;
            }
        }

        // Retenues éventuelles sur la somme remise au bénéficiaire.
        BigDecimal deductions = BigDecimal.ZERO;
        if (beneficiaryId != null && gross.signum() > 0) {
            deductions = deductSanctionsUseCase.deduct(beneficiaryId, gross, session,
                "Retenue sur grande tontine — séance " + session.getSessionDate(), user);
        }
        BigDecimal net = gross.subtract(deductions).max(BigDecimal.ZERO);

        // Le bénéficiaire n'est marqué comme servi qu'à la clôture de l'étape,
        // une fois toutes les cotisations de la séance saisies.
        if (beneficiaryId != null) {
            Long finalBeneficiaryId = beneficiaryId;
            tontineContributionRepository.findBySessionId(sessionId).stream()
                .findFirst()
                .ifPresent(c -> tontineParticipantRepository
                    .findByTourIdAndMemberId(c.getTourId(), finalBeneficiaryId)
                    .ifPresent(participant -> {
                        if (!participant.isHasBenefited()) {
                            participant.markAsBenefited();
                            tontineParticipantRepository.save(participant);
                        }
                    }));
        }

        SessionReportEntity report = sessionReportRepository.findBySessionId(sessionId)
            .orElseGet(() -> {
                SessionReportEntity r = new SessionReportEntity();
                r.setSessionId(sessionId);
                return r;
            });
        report.setTontineBeneficiaryId(beneficiaryId);
        report.setTontineGrossCollected(gross);
        report.setTontineSanctionDeductions(deductions);
        report.setTontineNetPaid(net);
        refreshCashFlows(report, sessionId);
        sessionReportRepository.save(report);

        session.setTontineClosedAt(LocalDateTime.now());
        log.info("Grande tontine clôturée — séance {} : collecté={}, retenues={}, remis={}, échecs={}",
            session.getSessionDate(), gross, deductions, net, defaults);
    }

    // ── REPORT_GENERATED ──────────────────────────────────────

    /**
     * Le rapport clôt la séance : elle passe au statut {@code closed} et libère
     * la place — une seule séance peut être en cours à la fois.
     */
    private void generateReport(MeetingSessionEntity session, AppUser user) {
        generateSessionReportUseCase.execute(session.getId(), user);
        LocalDateTime now = LocalDateTime.now();
        session.setReportGeneratedAt(now);
        session.setClosedAt(now);
        session.setStatus(SessionStatus.closed);
        log.info("Rapport généré, séance {} clôturée", session.getId());
    }

    // ── Entrées / sorties de caisse de la séance ──────────────

    /**
     * Recalcule les flux de caisse rattachés à la séance.
     *
     * <p>Les entrées du jour ne sont pas figées : développement, reliquat boisson,
     * sanctions encaissées, mais aussi tout mouvement saisi pendant la séance (un
     * don, par exemple). Le total remis au trésorier est donc la somme des
     * entrées de caisse de la séance moins ses sorties. Les tontines (remises en
     * main propre) et le retour dans les fonds de roulement ne transitent par
     * aucune caisse : ils en sont naturellement exclus.</p>
     *
     * <p>Quand les sorties dépassent les entrées, le solde ne devient pas
     * négatif : rien n'est remis au trésorier, et l'écart est reporté sur
     * {@code total_from_cashboxes} — le complément a été prélevé sur le solde
     * déjà détenu en caisse.</p>
     */
    void refreshCashFlows(SessionReportEntity report, Long sessionId) {
        List<Object[]> rows = entityManager.createQuery("""
                SELECT m.direction, m.origin, m.cashbox.type, SUM(m.amount)
                  FROM CashboxMovementEntity m
                 WHERE m.session.id = :sessionId
                 GROUP BY m.direction, m.origin, m.cashbox.type
                """, Object[].class)
            .setParameter("sessionId", sessionId)
            .getResultList();

        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;
        BigDecimal sanctions = BigDecimal.ZERO;
        BigDecimal other = BigDecimal.ZERO;

        for (Object[] row : rows) {
            MovementDirection direction = (MovementDirection) row[0];
            MovementOrigin origin       = (MovementOrigin) row[1];
            CashboxType type            = (CashboxType) row[2];
            BigDecimal amount           = (BigDecimal) row[3];

            if (direction == MovementDirection.in) {
                totalIn = totalIn.add(amount);
                if (type == CashboxType.sanction) {
                    sanctions = sanctions.add(amount);
                }
                if (origin == MovementOrigin.manual) {
                    other = other.add(amount);
                }
            } else {
                totalOut = totalOut.add(amount);
            }
        }

        // Les frais d'adhésion encaissés en séance ne transitent par aucune
        // caisse — ils vont directement au trésorier — mais ce sont bien des
        // entrées du jour : ils s'ajoutent au total remis.
        BigDecimal fees = membershipFeePaymentRepository.totalBySessionId(sessionId);
        if (fees == null) {
            fees = BigDecimal.ZERO;
        }
        totalIn = totalIn.add(fees);

        BigDecimal balance = totalIn.subtract(totalOut);
        report.setSanctionsCollected(sanctions);
        report.setMembershipFeesCollected(fees);
        report.setOtherIncome(other);
        report.setTotalOutflow(totalOut);
        report.setTotalToTreasurer(balance.max(BigDecimal.ZERO));
        report.setTotalFromCashboxes(balance.negate().max(BigDecimal.ZERO));
    }
}
