package org.asvosonk.session.application.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.asvosonk.cashbox.application.service.CashboxService;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;
import org.asvosonk.cashbox.domain.valueobject.MovementOrigin;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;
import org.asvosonk.sanction.domain.valueobject.SanctionOrigin;
import org.asvosonk.sanction.infrastructure.persistence.entity.SanctionEntity;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.session.domain.valueobject.AttendanceStatus;
import org.asvosonk.session.domain.valueobject.FundMovementType;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.asvosonk.session.infrastructure.persistence.entity.RevolvingFundEntity;
import org.asvosonk.session.infrastructure.persistence.entity.RevolvingFundMovementEntity;
import org.asvosonk.session.infrastructure.persistence.entity.SessionAttendanceEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Traitement de la cotisation de présence d'un membre, fond de roulement compris.
 *
 * <p><strong>Règles de l'association</strong></p>
 *
 * <p>Une cotisation de séance vaut 2 000 FCFA, répartis en 1 000 pour la tontine
 * du bénéficiaire du jour, 500 pour la boisson et 500 pour la caisse de
 * développement. Elle tombe à 1 000 FCFA (boisson + développement seulement)
 * quand le bénéficiaire du jour a rejoint le tour après que le cotisant a déjà
 * bénéficié — le montant dû est porté par la ligne de présence.</p>
 *
 * <p>Chaque membre dispose d'un fond de roulement de 5 000 FCFA, alimenté par le
 * frais d'adhésion du même nom. Il permet de couvrir deux séances entières
 * (2 × 2 000) puis, avec ses 1 000 derniers francs, de compléter un versement
 * partiel du membre : c'est le « compromis », après lequel le fond est épuisé.</p>
 *
 * <p><strong>Ordre d'imputation de l'argent apporté</strong> — l'espèce solde les
 * dettes les plus anciennes d'abord, puis la séance du jour :</p>
 * <ol>
 *   <li>avances du fond non remboursées (l'argent retourne dans le fond) ;</li>
 *   <li>échecs de cotisation encore dus (recouvrement, voir ci-dessous) ;</li>
 *   <li>cotisation de la séance tenante.</li>
 * </ol>
 *
 * <p>Quand l'espèce est épuisée, le fond prend le relais s'il en a les moyens,
 * dans le même ordre (échecs d'abord, puis séance tenante) ; chaque prise en
 * charge crée une nouvelle avance et une sanction de 200 FCFA. Si rien ne peut
 * couvrir la séance tenante, c'est l'échec : sanction de 500 FCFA, et la tontine,
 * la boisson et le développement du jour sont diminués d'autant.</p>
 *
 * <p><strong>Recouvrement d'un échec</strong> — la part de tontine (1 000 FCFA)
 * revient au bénéficiaire de la séance ratée, tandis que les 500 de boisson et
 * les 500 de développement alimentent la séance en cours.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RevolvingFundService {

    public static final BigDecimal FULL_FEE        = new BigDecimal("2000");
    public static final BigDecimal REDUCED_FEE     = new BigDecimal("1000");
    private static final BigDecimal BEVERAGE_SHARE    = new BigDecimal("500");
    private static final BigDecimal DEVELOPMENT_SHARE = new BigDecimal("500");
    private static final BigDecimal SANCTION_FUND_AMT    = new BigDecimal("200");
    private static final BigDecimal SANCTION_DEFAULT_AMT = new BigDecimal("500");

    private final CashboxService cashboxService;
    private final EntityManager  entityManager;

    private MemberEntity toEntity(Member m) {
        return m != null ? entityManager.getReference(MemberEntity.class, m.getId()) : null;
    }

    /**
     * Traite la cotisation d'un membre pour la séance en cours de clôture.
     *
     * @param member     membre traité
     * @param amountPaid espèces réellement apportées ce jour (0 possible)
     * @param session    séance en cours de clôture
     * @param attendance ligne de présence du membre pour cette séance
     * @param user       utilisateur connecté (piste d'audit des mouvements de caisse)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AttendanceResult process(Member member,
                                    BigDecimal amountPaid,
                                    MeetingSessionEntity session,
                                    SessionAttendanceEntity attendance,
                                    AppUser user) {
        return process(member, amountPaid, session, attendance, user, BigDecimal.ZERO);
    }

    /**
     * Variante utilisée pour le <strong>bénéficiaire de la tontine de présence</strong>.
     *
     * <p>Un bénéficiaire ne peut pas repartir avec sa tontine en restant endetté :
     * s'il n'apporte pas de quoi se mettre à jour, la régularisation est prélevée
     * d'office sur la tontine qu'il s'apprête à percevoir. {@code tontineAvailable}
     * est le montant mobilisable ; seul le strict nécessaire y est puisé, jamais
     * plus que ce qu'il doit.</p>
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AttendanceResult process(Member member,
                                    BigDecimal amountPaid,
                                    MeetingSessionEntity session,
                                    SessionAttendanceEntity attendance,
                                    AppUser user,
                                    BigDecimal tontineAvailable) {

        AttendanceResult result = new AttendanceResult(member);
        RevolvingFundEntity fund = fundOf(member);
        BigDecimal cash = amountPaid != null ? amountPaid : BigDecimal.ZERO;
        attendance.setAmountPaid(cash);

        // ── 1. Dettes antérieures, de la plus ancienne à la plus récente ──
        List<Debt> debts = outstandingDebts(member, session);

        // ── 1 bis. Régularisation obligatoire du bénéficiaire ────────────
        // Ce qui manque pour tout solder — dettes antérieures et cotisation du
        // jour — est prélevé sur sa tontine, dans la limite de celle-ci.
        BigDecimal purse = tontineAvailable != null ? tontineAvailable : BigDecimal.ZERO;
        if (purse.signum() > 0) {
            BigDecimal owed = attendance.getAmountDue();
            for (Debt debt : debts) {
                owed = owed.add(debt.amount());
            }
            BigDecimal missing = owed.subtract(cash).max(BigDecimal.ZERO);
            BigDecimal drawn = missing.min(purse);
            if (drawn.signum() > 0) {
                cash = cash.add(drawn);
                result.setFromTontine(drawn);
                log.info("Bénéficiaire {} : {} FCFA prélevés sur sa tontine pour le mettre à jour",
                    member.getFullName(), drawn);
            }
        }

        for (Debt debt : debts) {
            if (cash.compareTo(debt.amount()) < 0) {
                break;   // l'espèce ne suffit plus : la priorité chronologique s'arrête ici
            }
            cash = cash.subtract(debt.amount());
            if (debt.isAdvance()) {
                repayAdvance(debt.advance(), fund);
                result.addReturnToFund(debt.amount());
            } else {
                recoverFailure(debt.failure(), session, result, member, user);
            }
        }

        // ── 2. Le fond reprend les échecs restants s'il en a les moyens ──
        for (Debt debt : debts) {
            if (debt.isAdvance() || debt.failure().getRecoveredSession() != null) {
                continue;
            }
            if (!fund.hasSufficientBalance(debt.amount())) {
                break;
            }
            advanceFund(fund, debt.failure().getSession(), debt.amount());
            createSanction(member, session, SANCTION_FUND_AMT,
                "Échec de la séance du " + debt.failure().getSession().getSessionDate()
                    + " repris par le fond de roulement",
                SanctionOrigin.presence_fund, debt.failure().getId());
            recoverFailure(debt.failure(), session, result, member, user);
        }

        // ── 3. Cotisation de la séance tenante ──────────────────────────
        BigDecimal due       = attendance.getAmountDue();
        BigDecimal fromCash  = cash.min(due);
        BigDecimal shortfall = due.subtract(fromCash);

        if (shortfall.signum() == 0) {
            attendance.setAttendanceStatus(AttendanceStatus.up_to_date);
            result.setStatus(AttendanceStatus.up_to_date);
            distribute(attendance, session, member, result, user, "Présence séance ");
            cash = cash.subtract(fromCash);

        } else if (fund.hasSufficientBalance(shortfall)) {
            // Le fond complète : prise en charge totale (2 000) ou compromis
            // (le membre apporte une partie, le fond met le reste).
            advanceFund(fund, session, shortfall);
            createSanction(member, session, SANCTION_FUND_AMT,
                fromCash.signum() > 0
                    ? "Cotisé par le fond de roulement (complément de "
                        + shortfall.toBigInteger() + " FCFA)"
                    : "Cotisé par le fond de roulement",
                SanctionOrigin.presence_fund, attendance.getId());
            attendance.setCoveredByFund(true);
            attendance.setAttendanceStatus(AttendanceStatus.covered_by_fund);
            result.setStatus(AttendanceStatus.covered_by_fund);
            result.setPartialFund(fromCash.signum() > 0);
            distribute(attendance, session, member, result, user, "Présence (fond) séance ");
            cash = cash.subtract(fromCash);

        } else {
            // Ni l'espèce ni le fond ne couvrent la séance : échec.
            attendance.setCoveredByFund(false);
            attendance.setAttendanceStatus(AttendanceStatus.default_status);
            attendance.setRecoveredSession(null);
            result.setStatus(AttendanceStatus.default_status);
            createSanction(member, session, SANCTION_DEFAULT_AMT,
                "Échec de cotisation de présence", SanctionOrigin.presence_default,
                attendance.getId());
        }

        // ── 4. Reliquat d'espèces inutilisable ──────────────────────────
        // Il ne peut rester que de l'argent trop faible pour solder quoi que ce
        // soit. Il est versé au fond de roulement du membre — le seul endroit où
        // son argent peut attendre sans être dépensé — et sera consommé dès la
        // prochaine prise en charge. Le prélèvement sur tontine, plafonné au dû,
        // ne peut jamais alimenter ce reliquat.
        if (cash.signum() > 0) {
            fund.setBalance(fund.getBalance().add(cash));
            entityManager.merge(fund);
            recordMovement(fund, session, FundMovementType.repayment, cash, true);
            result.addReturnToFund(cash);
            result.setUnallocatedCash(cash);
            log.info("Membre {} : reliquat de {} FCFA versé au fond de roulement",
                member.getFullName(), cash);
        }

        entityManager.merge(attendance);
        return result;
    }

    // ── Dettes ───────────────────────────────────────────────────────────

    /**
     * Avances non remboursées et échecs non recouverts, toutes séances antérieures
     * confondues, triés par date de séance croissante.
     */
    private List<Debt> outstandingDebts(Member member, MeetingSessionEntity session) {
        List<RevolvingFundMovementEntity> advances = entityManager.createQuery("""
                SELECT m FROM RevolvingFundMovementEntity m
                 WHERE m.fund.member.id = :memberId
                   AND m.movementType = :advance
                   AND m.recovered = false
                   AND m.session.id <> :sessionId
                """, RevolvingFundMovementEntity.class)
            .setParameter("memberId", member.getId())
            .setParameter("advance", FundMovementType.advance)
            .setParameter("sessionId", session.getId())
            .getResultList();

        List<SessionAttendanceEntity> failures = entityManager.createQuery("""
                SELECT a FROM SessionAttendanceEntity a
                 WHERE a.member.id = :memberId
                   AND a.attendanceStatus = :failed
                   AND a.recoveredSession IS NULL
                   AND a.session.id <> :sessionId
                """, SessionAttendanceEntity.class)
            .setParameter("memberId", member.getId())
            .setParameter("failed", AttendanceStatus.default_status)
            .setParameter("sessionId", session.getId())
            .getResultList();

        List<Debt> debts = new ArrayList<>();
        advances.forEach(a -> debts.add(Debt.ofAdvance(a)));
        failures.forEach(f -> debts.add(Debt.ofFailure(f)));
        debts.sort(Comparator.comparing(Debt::date));
        return debts;
    }

    /** Une dette antérieure : soit une avance du fond, soit un échec de cotisation. */
    private record Debt(RevolvingFundMovementEntity advance, SessionAttendanceEntity failure) {

        static Debt ofAdvance(RevolvingFundMovementEntity a) { return new Debt(a, null); }
        static Debt ofFailure(SessionAttendanceEntity f)     { return new Debt(null, f); }

        boolean isAdvance() { return advance != null; }

        BigDecimal amount() {
            return isAdvance() ? advance.getAmount() : failure.getAmountDue();
        }

        java.time.LocalDate date() {
            return isAdvance() ? advance.getSession().getSessionDate()
                               : failure.getSession().getSessionDate();
        }
    }

    // ── Répartition d'une cotisation ─────────────────────────────────────

    /**
     * Répartit une cotisation acquittée : part de tontine au bénéficiaire du jour,
     * 500 pour la boisson, 500 pour la caisse de développement.
     */
    private void distribute(SessionAttendanceEntity attendance,
                            MeetingSessionEntity session,
                            Member member,
                            AttendanceResult result,
                            AppUser user,
                            String reasonPrefix) {
        result.setContributionToTontine(attendance.tontineShare());
        result.setContributionToBeverage(BEVERAGE_SHARE);
        result.setContributionToDevelopment(DEVELOPMENT_SHARE);

        cashboxService.credit(CashboxType.development, DEVELOPMENT_SHARE,
            reasonPrefix + session.getSessionDate(), MovementOrigin.presence,
            session, toEntity(member), attendance.getId(), user);
    }

    /**
     * Recouvre un échec : la part de tontine revient au bénéficiaire de la séance
     * ratée (remise en espèces), la boisson et le développement alimentent la
     * séance en cours.
     */
    private void recoverFailure(SessionAttendanceEntity failure,
                                MeetingSessionEntity currentSession,
                                AttendanceResult result,
                                Member member,
                                AppUser user) {
        failure.setAttendanceStatus(AttendanceStatus.recovered);
        failure.setRecoveredSession(currentSession);
        entityManager.merge(failure);

        BigDecimal tontineShare = failure.tontineShare();
        if (tontineShare.signum() > 0) {
            MemberEntity pastBeneficiary = failure.getSession().getPresenceBeneficiary();
            result.addRecovery(new Recovery(
                failure.getSession().getId(),
                failure.getSession().getSessionDate(),
                pastBeneficiary != null ? pastBeneficiary.getId() : null,
                tontineShare));
        }

        result.addRecoveredBeverage(BEVERAGE_SHARE);
        result.addRecoveredDevelopment(DEVELOPMENT_SHARE);

        cashboxService.credit(CashboxType.development, DEVELOPMENT_SHARE,
            "Recouvrement de la séance du " + failure.getSession().getSessionDate(),
            MovementOrigin.presence, currentSession, toEntity(member), failure.getId(), user);

        log.info("Échec du {} recouvert par {} lors de la séance du {}",
            failure.getSession().getSessionDate(), member.getFullName(),
            currentSession.getSessionDate());
    }

    // ── Fond de roulement ────────────────────────────────────────────────

    private RevolvingFundEntity fundOf(Member member) {
        return entityManager.createQuery(
                "SELECT f FROM RevolvingFundEntity f WHERE f.member.id = :memberId",
                RevolvingFundEntity.class)
            .setParameter("memberId", member.getId())
            .getResultStream()
            .findFirst()
            .orElseGet(() -> {
                RevolvingFundEntity newFund = new RevolvingFundEntity();
                newFund.setMember(toEntity(member));
                entityManager.persist(newFund);
                return newFund;
            });
    }

    private void advanceFund(RevolvingFundEntity fund, MeetingSessionEntity session, BigDecimal amount) {
        fund.setBalance(fund.getBalance().subtract(amount));
        entityManager.merge(fund);
        recordMovement(fund, session, FundMovementType.advance, amount, false);
    }

    private void repayAdvance(RevolvingFundMovementEntity advance, RevolvingFundEntity fund) {
        advance.setRecovered(true);
        entityManager.merge(advance);
        fund.setBalance(fund.getBalance().add(advance.getAmount()));
        entityManager.merge(fund);
    }

    private void recordMovement(RevolvingFundEntity fund, MeetingSessionEntity session,
                                FundMovementType type, BigDecimal amount, boolean recovered) {
        RevolvingFundMovementEntity mv = new RevolvingFundMovementEntity();
        mv.setFund(fund);
        mv.setSession(session);
        mv.setMovementType(type);
        mv.setAmount(amount);
        mv.setRecovered(recovered);
        entityManager.persist(mv);
    }

    private void createSanction(Member member, MeetingSessionEntity session,
                                BigDecimal amount, String reason,
                                SanctionOrigin origin, Long referenceId) {
        SanctionEntity s = new SanctionEntity();
        s.setMember(toEntity(member));
        s.setSanctionDate(session.getSessionDate());
        s.setAmount(amount);
        s.setReason(reason);
        s.setOrigin(origin);
        s.setReferenceId(referenceId);
        entityManager.persist(s);
    }

    // ── Résultats ────────────────────────────────────────────────────────

    /** Rattrapage dû au bénéficiaire d'une séance dont l'échec vient d'être recouvert. */
    public record Recovery(Long failedSessionId,
                           java.time.LocalDate failedSessionDate,
                           Long beneficiaryId,
                           BigDecimal amount) { }

    @lombok.Getter @lombok.Setter
    public static class AttendanceResult {
        private final Member member;
        private AttendanceStatus status;
        private BigDecimal contributionToTontine     = BigDecimal.ZERO;
        private BigDecimal contributionToBeverage    = BigDecimal.ZERO;
        private BigDecimal contributionToDevelopment = BigDecimal.ZERO;
        /** Boisson et développement apportés par le recouvrement d'anciens échecs. */
        private BigDecimal recoveredBeverage    = BigDecimal.ZERO;
        private BigDecimal recoveredDevelopment = BigDecimal.ZERO;
        /** Argent retourné dans le fond de roulement du membre. */
        private BigDecimal returnToFund = BigDecimal.ZERO;
        /** Part de sa propre tontine prélevée pour mettre le bénéficiaire à jour. */
        private BigDecimal fromTontine = BigDecimal.ZERO;
        /** Reliquat d'espèces non imputable, versé au fond. */
        private BigDecimal unallocatedCash = BigDecimal.ZERO;
        private boolean partialFund = false;
        private final List<Recovery> recoveries = new ArrayList<>();

        public AttendanceResult(Member member) { this.member = member; }

        void addReturnToFund(BigDecimal amount)       { this.returnToFund = returnToFund.add(amount); }
        void addRecoveredBeverage(BigDecimal amount)  { this.recoveredBeverage = recoveredBeverage.add(amount); }
        void addRecoveredDevelopment(BigDecimal amount) { this.recoveredDevelopment = recoveredDevelopment.add(amount); }
        void addRecovery(Recovery recovery)           { this.recoveries.add(recovery); }

        public boolean isDefault()      { return status == AttendanceStatus.default_status; }
        public boolean isCoveredByFund(){ return status == AttendanceStatus.covered_by_fund; }
        public boolean isUpToDate()     { return status == AttendanceStatus.up_to_date; }

        /** Total boisson apporté par ce membre : cotisation du jour + recouvrements. */
        public BigDecimal totalBeverage()    { return contributionToBeverage.add(recoveredBeverage); }
        /** Total développement apporté par ce membre : cotisation du jour + recouvrements. */
        public BigDecimal totalDevelopment() { return contributionToDevelopment.add(recoveredDevelopment); }
    }
}
