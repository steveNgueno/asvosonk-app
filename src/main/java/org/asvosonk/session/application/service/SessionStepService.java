package org.asvosonk.session.application.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.asvosonk.cashbox.application.service.CashboxService;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;
import org.asvosonk.cashbox.domain.valueobject.MovementOrigin;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.repository.MemberRepository;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;
import org.asvosonk.presence.application.usecase.GetCurrentPresenceBeneficiaryUseCase;
import org.asvosonk.presence.application.usecase.MarkPresenceBenefitedUseCase;
import org.asvosonk.presence.domain.repository.PresenceTourRepository;
import org.asvosonk.sanction.domain.valueobject.SanctionStatus;
import org.asvosonk.sanction.infrastructure.persistence.entity.SanctionEntity;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.session.domain.valueobject.SessionStep;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.asvosonk.session.infrastructure.persistence.entity.SessionAttendanceEntity;
import org.asvosonk.session.application.usecase.GenerateSessionReportUseCase;
import org.asvosonk.session.infrastructure.persistence.entity.SessionReportEntity;
import org.asvosonk.session.infrastructure.persistence.repository.SessionReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the sequential workflow step transitions for a meeting session.
 * Each step must be completed before advancing to the next.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionStepService {

    private static final BigDecimal PRESENCE_FEE            = new BigDecimal("2000");
    private static final BigDecimal BEVERAGE_COST_PER_PERSON = new BigDecimal("500");

    private final SessionService                       sessionService;
    private final EntityManager                        entityManager;
    private final MemberRepository                     memberRepository;
    private final RevolvingFundService                 revolvingFundService;
    private final CashboxService                       cashboxService;
    private final SessionReportRepository              sessionReportRepository;
    private final PresenceTourRepository               presenceTourRepository;
    private final GetCurrentPresenceBeneficiaryUseCase getCurrentPresenceBeneficiaryUseCase;
    private final MarkPresenceBenefitedUseCase         markPresenceBenefitedUseCase;
    private final GenerateSessionReportUseCase         generateSessionReportUseCase;

    /**
     * Transit the session to the next step in the workflow.
     */
    @Transactional
    public MeetingSessionEntity transitionToNext(Long sessionId, AppUser user) {
        return transitionToNext(sessionId, user, null);
    }

    /**
     * Advance the session to the next workflow step, idempotently (F-10).
     *
     * <p>Protections against replay / double-submit:
     * <ul>
     *   <li>the session row is loaded under a PESSIMISTIC_WRITE lock, so two
     *       concurrent submits are serialized;</li>
     *   <li>a session already at the final step is rejected;</li>
     *   <li>if {@code expectedCurrent} is provided (PRG token from the form),
     *       it must match the actual current step — a stale re-post whose step
     *       has already advanced is rejected instead of triggering another
     *       financial transition.</li>
     * </ul>
     */
    @Transactional
    public MeetingSessionEntity transitionToNext(Long sessionId, AppUser user, SessionStep expectedCurrent) {
        // Pessimistic lock: serialize concurrent transitions on this session.
        MeetingSessionEntity session = sessionService.findByIdForUpdate(sessionId);
        SessionStep current = session.getCurrentStepEnum();

        // Idempotence guard: reject a replay whose expected step no longer matches.
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

        log.info("Session {} transitioned from {} to {}", session.getId(), current, next);
        return session;
    }

    // ── Execute a single transition ──────────────────────────

    private void executeTransition(MeetingSessionEntity session, SessionStep step, AppUser user) {
        switch (step) {
            case PRESENCE_OPEN      -> openPresence(session);
            case PRESENCE_CLOSED    -> closePresence(session, user);
            case TONTINE_OPEN       -> { }
            case TONTINE_CLOSED     -> closeTontine(session, user);
            case REPORT_GENERATED   -> generateReport(session, user);
            default                 -> { }
        }
    }

    // ── PRESENCE_OPEN ─────────────────────────────────────────

    private void openPresence(MeetingSessionEntity session) {
        var beneficiary = getCurrentPresenceBeneficiaryUseCase.peekNextBeneficiary();
        if (beneficiary != null) {
            MemberEntity memberEntity = entityManager.getReference(MemberEntity.class, beneficiary.getMemberId());
            session.setPresenceBeneficiary(memberEntity);
        }
    }

    // ── PRESENCE_CLOSED — full financial processing ───────────

    private void closePresence(MeetingSessionEntity session, AppUser user) {
        Long sessionId = session.getId();

        List<SessionAttendanceEntity> attendances = entityManager.createQuery(
                "SELECT a FROM SessionAttendanceEntity a JOIN a.member m WHERE a.session.id = :sessionId ORDER BY m.fullName ASC",
                SessionAttendanceEntity.class)
            .setParameter("sessionId", sessionId)
            .getResultList();

        // Accumulators
        BigDecimal totalTontine      = BigDecimal.ZERO;
        BigDecimal totalBeveragePool = BigDecimal.ZERO;
        BigDecimal totalDevelopment  = BigDecimal.ZERO;
        int presentCount = 0, fundCoveredCount = 0, defaultCount = 0;
        List<RevolvingFundService.AttendanceResult> results = new ArrayList<>();

        // ── Process each member via RevolvingFund ────────────
        for (SessionAttendanceEntity att : attendances) {
            Member member = memberRepository.findById(att.getMember().getId()).orElse(null);
            if (member == null) continue;

            RevolvingFundService.AttendanceResult r =
                revolvingFundService.process(member, att.getAmountPaid(), session, att, user);
            results.add(r);
            entityManager.merge(att);

            totalTontine      = totalTontine.add(r.getContributionToTontine());
            totalBeveragePool = totalBeveragePool.add(r.getContributionToBeverage());
            totalDevelopment  = totalDevelopment.add(r.getContributionToDevelopment());
            if (att.isPresent())   presentCount++;
            if (r.isCoveredByFund()) fundCoveredCount++;
            if (r.isDefault())       defaultCount++;
        }

        // ── Beverage reliquat ───────────────────────────────
        BigDecimal actualBeverageCost = BEVERAGE_COST_PER_PERSON.multiply(BigDecimal.valueOf(presentCount));
        BigDecimal beverageReliquat = totalBeveragePool.subtract(actualBeverageCost).max(BigDecimal.ZERO);

        if (beverageReliquat.compareTo(BigDecimal.ZERO) > 0) {
            cashboxService.credit(CashboxType.beverage, beverageReliquat,
                "Reliquat boisson séance " + session.getSessionDate(),
                MovementOrigin.presence, session, null, null, user);
        }

        // ── Sanctions on beneficiary ────────────────────────
        MemberEntity beneficiaryEntity = session.getPresenceBeneficiary();
        BigDecimal totalSanctionDeductions = BigDecimal.ZERO;

        if (beneficiaryEntity != null) {
            // F-06 : on ne peut retenir sur la tontine du bénéficiaire QUE ce que
            // cette tontine contient réellement. Créditer la caisse du total des
            // sanctions alors que la tontine nette est plafonnée à 0 fabriquerait
            // de l'argent. On solde donc les sanctions les plus anciennes d'abord,
            // dans la limite du montant de tontine disponible.
            TypedQuery<SanctionEntity> sQuery = entityManager.createQuery(
                "SELECT s FROM SanctionEntity s WHERE s.member.id = :memberId AND s.status = :status ORDER BY s.sanctionDate ASC",
                SanctionEntity.class);
            sQuery.setParameter("memberId", beneficiaryEntity.getId());
            sQuery.setParameter("status", SanctionStatus.unpaid);
            List<SanctionEntity> unpaidSanctions = sQuery.getResultList();

            // @TODO-BUREAU : politique de couverture partielle. Choix actuel (sûr) :
            // une sanction n'est soldée que si la tontine disponible la couvre
            // ENTIÈREMENT ; aucun paiement partiel n'est enregistré. À confirmer.
            BigDecimal available = totalTontine;   // montant de tontine mobilisable
            for (SanctionEntity s : unpaidSanctions) {
                if (available.compareTo(s.getAmount()) < 0) {
                    break;   // plus assez de tontine pour couvrir cette sanction (ni les suivantes)
                }
                s.setStatus(SanctionStatus.paid);
                s.setPaymentDate(LocalDate.now());
                entityManager.merge(s);
                available = available.subtract(s.getAmount());
                totalSanctionDeductions = totalSanctionDeductions.add(s.getAmount());
            }

            if (totalSanctionDeductions.compareTo(BigDecimal.ZERO) > 0) {
                cashboxService.credit(CashboxType.sanction, totalSanctionDeductions,
                    "Sanctions retenues sur tontine de présence",
                    MovementOrigin.sanction, session, beneficiaryEntity, null, user);
            }

            // ── Mark beneficiary in PresenceTour ────────────
            presenceTourRepository.findCurrentOpenTour().ifPresent(openTour -> {
                try {
                    markPresenceBenefitedUseCase.execute(
                        openTour.getId(),
                        beneficiaryEntity.getId(),
                        sessionId
                    );
                } catch (Exception e) {
                    log.warn("Could not mark presence beneficiary: {}", e.getMessage());
                }
            });
        }

        BigDecimal netTontine = totalTontine.subtract(totalSanctionDeductions).max(BigDecimal.ZERO);

        // ── Persist session report ──────────────────────────
        SessionReportEntity report = new SessionReportEntity();
        report.setSessionId(sessionId);
        report.setPresenceBeneficiaryId(
            beneficiaryEntity != null ? beneficiaryEntity.getId() : null);
        report.setPresenceTotalCotisants(attendances.size());
        report.setPresencePresentCount(presentCount);
        report.setPresenceFundCoveredCount(fundCoveredCount);
        report.setPresenceDefaultCount(defaultCount);
        report.setPresenceGrossTontine(totalTontine);
        report.setPresenceSanctionDeductions(totalSanctionDeductions);
        report.setPresenceNetTontine(netTontine);
        report.setPresenceDevelopmentTotal(totalDevelopment);
        report.setPresenceBeverageReliquat(beverageReliquat);
        report.setTotalToTreasurer(netTontine.add(totalDevelopment));
        sessionReportRepository.save(report);

        // ── Mark presence closed ────────────────────────────
        session.setPresenceClosedAt(LocalDateTime.now());

        log.info("Presence closed for session {} — netTontine={}, beverageReliquat={}, defaults={}",
                 session.getSessionDate(), netTontine, beverageReliquat, defaultCount);
    }

    // ── TONTINE_CLOSED ────────────────────────────────────────

    private void closeTontine(MeetingSessionEntity session, AppUser user) {
        Long sessionId = session.getId();

        // ── Aggregate tontine contributions for this session ──
        List<Object[]> contribs = entityManager.createQuery(
                "SELECT c.amount, c.status " +
                "FROM TontineContributionEntity c WHERE c.sessionId = :sessionId", Object[].class)
            .setParameter("sessionId", sessionId)
            .getResultList();

        BigDecimal tontineGrossCollected = BigDecimal.ZERO;
        int defaultCount = 0;

        for (Object[] row : contribs) {
            BigDecimal amount = (BigDecimal) row[0];
            String status = row[1].toString();
            if ("paid".equals(status)) {
                tontineGrossCollected = tontineGrossCollected.add(amount);
            } else {
                defaultCount++;
            }
        }

        // ── Update existing report with tontine data ──────────
        var reportOpt = sessionReportRepository.findBySessionId(sessionId);
        if (reportOpt.isPresent()) {
            SessionReportEntity report = reportOpt.get();
            report.setTontineGrossCollected(tontineGrossCollected);
            report.setTontineNetPaid(tontineGrossCollected);
            BigDecimal presenceTotal = report.getPresenceNetTontine() != null
                ? report.getPresenceNetTontine() : BigDecimal.ZERO;
            BigDecimal presenceDev = report.getPresenceDevelopmentTotal() != null
                ? report.getPresenceDevelopmentTotal() : BigDecimal.ZERO;
            report.setTotalToTreasurer(presenceTotal.add(presenceDev).add(tontineGrossCollected));
            sessionReportRepository.save(report);
        }

        session.setTontineClosedAt(LocalDateTime.now());
        log.info("Tontine closed for session {} — collected={}, defaults={}",
            session.getSessionDate(), tontineGrossCollected, defaultCount);
    }

    // ── REPORT_GENERATED ──────────────────────────────────────

    private void generateReport(MeetingSessionEntity session, AppUser user) {
        generateSessionReportUseCase.execute(session.getId(), user);
        session.setReportGeneratedAt(LocalDateTime.now());
        log.info("Report generated for session {}", session.getId());
    }
}
