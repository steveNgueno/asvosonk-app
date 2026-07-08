package org.asvosonk.session.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;
import org.asvosonk.cashbox.domain.valueobject.MovementOrigin;
import org.asvosonk.cashbox.application.service.CashboxService;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;
import org.asvosonk.sanction.domain.valueobject.SanctionOrigin;
import org.asvosonk.sanction.infrastructure.persistence.entity.SanctionEntity;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.infrastructure.persistence.entity.AppUserEntity;
import jakarta.persistence.EntityManager;
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
import java.time.LocalDate;
import java.util.List;

/**
 * Implements the full revolving fund logic (scenarios 1 / 2 / 3).
 *
 * Amounts:
 *   PRESENCE_FEE      = 2 000 FCFA  (full session cotisation)
 *   TONTINE_SHARE     = 1 000 FCFA  → to beneficiary
 *   BEVERAGE_SHARE    =   500 FCFA  → cashbox beverage
 *   DEVELOPMENT_SHARE =   500 FCFA  → cashbox development
 *   SANCTION_FUND     =   200 FCFA  (covered by fund)
 *   SANCTION_DEFAULT  =   500 FCFA  (full default)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RevolvingFundService {

    private static final BigDecimal PRESENCE_FEE      = new BigDecimal("2000");
    private static final BigDecimal TONTINE_SHARE     = new BigDecimal("1000");
    private static final BigDecimal BEVERAGE_SHARE    = new BigDecimal("500");
    private static final BigDecimal DEVELOPMENT_SHARE = new BigDecimal("500");
    private static final BigDecimal SANCTION_FUND_AMT = new BigDecimal("200");
    private static final BigDecimal SANCTION_DEFAULT_AMT = new BigDecimal("500");
    private static final BigDecimal PARTIAL_FUND_AMT  = new BigDecimal("1000");

    private final CashboxService                  cashboxService;
    private final EntityManager                   entityManager;

    private MemberEntity toEntity(Member m) {
        return m != null ? entityManager.getReference(MemberEntity.class, m.getId()) : null;
    }

    /**
     * Main entry point called by SessionService for each member during session close.
     *
     * @param member      the member being processed
     * @param amountPaid  amount the member physically paid this session (may be 0)
     * @param session     the current session
     * @param attendance  the attendance record to update with final status
     * @param user        logged-in user (for audit trail)
     * @return SessionResult containing what happened (for the session report)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AttendanceResult process(Member member,
                                    BigDecimal amountPaid,
                                    MeetingSessionEntity session,
                                    SessionAttendanceEntity attendance,
                                    AppUser user) {

        RevolvingFundEntity fund = entityManager.createQuery(
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

        List<RevolvingFundMovementEntity> pendingAdvances = entityManager.createQuery(
                "SELECT m FROM RevolvingFundMovementEntity m WHERE m.fund.member.id = :memberId AND m.movementType = 'advance' AND m.recovered = false ORDER BY m.session.sessionDate ASC",
                RevolvingFundMovementEntity.class)
            .setParameter("memberId", member.getId())
            .getResultList();
        int pending = pendingAdvances.size();

        log.debug("Processing member {} | amountPaid={} | pendingAdvances={}",
                  member.getFullName(), amountPaid, pending);

        return switch (pending) {
            case 0  -> processScenario1(member, amountPaid, session, attendance, fund, user);
            case 1  -> processScenario2(member, amountPaid, session, attendance, fund,
                                         pendingAdvances.get(0), user);
            default -> processScenario3(member, amountPaid, session, attendance, fund,
                                         pendingAdvances, user);
        };
    }

    // ── SCENARIO 1 : no pending debt ─────────────────────────────────────────

    private AttendanceResult processScenario1(Member member, BigDecimal amountPaid,
                                               MeetingSessionEntity session,
                                               SessionAttendanceEntity attendance,
                                               RevolvingFundEntity fund, AppUser user) {
        AttendanceResult result = new AttendanceResult(member);

        if (amountPaid.compareTo(PRESENCE_FEE) >= 0) {
            // Normal payment — all good
            attendance.setAmountPaid(PRESENCE_FEE);
            attendance.setAttendanceStatus(AttendanceStatus.up_to_date);
            result.setStatus(AttendanceStatus.up_to_date);
            result.setContributionToTontine(TONTINE_SHARE);
            result.setContributionToBeverage(BEVERAGE_SHARE);
            result.setContributionToDevelopment(DEVELOPMENT_SHARE);

            // Credit cashboxes
            cashboxService.credit(CashboxType.development, DEVELOPMENT_SHARE,
                "Présence séance " + session.getSessionDate(), MovementOrigin.presence,
                session, toEntity(member), attendance.getId(), user);

        } else if (amountPaid.compareTo(BigDecimal.ZERO) == 0
                   && fund.hasSufficientBalance(PRESENCE_FEE)) {
            // Fund covers the session
            advanceFund(fund, session, PRESENCE_FEE);
            createSanction(member, session, SANCTION_FUND_AMT,
                           "Cotisé par le fond de roulement", SanctionOrigin.presence_fund,
                           attendance.getId());
            attendance.setCoveredByFund(true);
            attendance.setAttendanceStatus(AttendanceStatus.covered_by_fund);
            result.setStatus(AttendanceStatus.covered_by_fund);
            result.setContributionToTontine(TONTINE_SHARE);
            result.setContributionToBeverage(BEVERAGE_SHARE);
            result.setContributionToDevelopment(DEVELOPMENT_SHARE);

            cashboxService.credit(CashboxType.development, DEVELOPMENT_SHARE,
                "Présence (fond) séance " + session.getSessionDate(), MovementOrigin.presence,
                session, toEntity(member), attendance.getId(), user);

        } else {
            // Default
            processDefault(member, session, attendance, result, user);
        }

        return result;
    }

    // ── SCENARIO 2 : 1 pending advance ───────────────────────────────────────

    private AttendanceResult processScenario2(Member member, BigDecimal amountPaid,
                                               MeetingSessionEntity session,
                                               SessionAttendanceEntity attendance,
                                               RevolvingFundEntity fund,
                                               RevolvingFundMovementEntity oldAdvance,
                                               AppUser user) {
        AttendanceResult result = new AttendanceResult(member);
        attendance.setAmountPaid(amountPaid);

        if (amountPaid.compareTo(new BigDecimal("4000")) >= 0) {
            // Repay previous advance + pay current session
            recoverAdvance(oldAdvance, fund);
            recordRepayment(fund, session, PRESENCE_FEE);
            attendance.setAttendanceStatus(AttendanceStatus.up_to_date);
            result.setStatus(AttendanceStatus.up_to_date);
            result.setContributionToTontine(TONTINE_SHARE);
            result.setContributionToBeverage(BEVERAGE_SHARE);
            result.setContributionToDevelopment(DEVELOPMENT_SHARE);
            cashboxService.credit(CashboxType.development, DEVELOPMENT_SHARE,
                "Présence séance " + session.getSessionDate(), MovementOrigin.presence,
                session, toEntity(member), attendance.getId(), user);

        } else if (amountPaid.compareTo(PRESENCE_FEE) >= 0) {
            // Repay previous advance only; current session covered by fund again
            recoverAdvance(oldAdvance, fund);
            if (fund.hasSufficientBalance(PRESENCE_FEE)) {
                advanceFund(fund, session, PRESENCE_FEE);
                createSanction(member, session, SANCTION_FUND_AMT,
                               "Cotisé par le fond (séance tenante)", SanctionOrigin.presence_fund,
                               attendance.getId());
                attendance.setCoveredByFund(true);
                attendance.setAttendanceStatus(AttendanceStatus.covered_by_fund);
                result.setStatus(AttendanceStatus.covered_by_fund);
                result.setContributionToTontine(TONTINE_SHARE);
                result.setContributionToBeverage(BEVERAGE_SHARE);
                result.setContributionToDevelopment(DEVELOPMENT_SHARE);
                cashboxService.credit(CashboxType.development, DEVELOPMENT_SHARE,
                    "Présence (fond) séance " + session.getSessionDate(), MovementOrigin.presence,                    session, toEntity(member), attendance.getId(), user);

            } else {
                processDefault(member, session, attendance, result, user);
            }

        } else if (amountPaid.compareTo(BigDecimal.ZERO) == 0) {
            // Nothing paid — try to cover current session with fund
            if (fund.hasSufficientBalance(PRESENCE_FEE)) {
                advanceFund(fund, session, PRESENCE_FEE);
                createSanction(member, session, SANCTION_FUND_AMT,
                               "Cotisé par le fond de roulement (2e séance)", SanctionOrigin.presence_fund,
                               attendance.getId());
                attendance.setCoveredByFund(true);
                attendance.setAttendanceStatus(AttendanceStatus.covered_by_fund);
                result.setStatus(AttendanceStatus.covered_by_fund);
                result.setContributionToTontine(TONTINE_SHARE);
                result.setContributionToBeverage(BEVERAGE_SHARE);
                result.setContributionToDevelopment(DEVELOPMENT_SHARE);
                cashboxService.credit(CashboxType.development, DEVELOPMENT_SHARE,
                    "Présence (fond) séance " + session.getSessionDate(), MovementOrigin.presence,                    session, toEntity(member), attendance.getId(), user);
            } else {
                processDefault(member, session, attendance, result, user);
            }

        } else {
            // Partial amount — not enough to cover anything meaningful
            processDefault(member, session, attendance, result, user);
        }

        return result;
    }

    // ── SCENARIO 3 : 2+ pending advances (may lead to default) ───────────────

    private AttendanceResult processScenario3(Member member, BigDecimal amountPaid,
                                               MeetingSessionEntity session,
                                               SessionAttendanceEntity attendance,
                                               RevolvingFundEntity fund,
                                               List<RevolvingFundMovementEntity> pendingAdvances,
                                               AppUser user) {
        AttendanceResult result = new AttendanceResult(member);
        attendance.setAmountPaid(amountPaid);

        RevolvingFundMovementEntity oldest  = pendingAdvances.get(0);
        RevolvingFundMovementEntity secondOldest = pendingAdvances.get(1);

        if (amountPaid.compareTo(new BigDecimal("6000")) >= 0) {
            // Repay both advances + pay current
            recoverAdvance(oldest, fund);
            recoverAdvance(secondOldest, fund);
            recordRepayment(fund, session, PRESENCE_FEE);
            attendance.setAttendanceStatus(AttendanceStatus.up_to_date);
            result.setStatus(AttendanceStatus.up_to_date);
            result.setContributionToTontine(TONTINE_SHARE);
            result.setContributionToBeverage(BEVERAGE_SHARE);
            result.setContributionToDevelopment(DEVELOPMENT_SHARE);
            cashboxService.credit(CashboxType.development, DEVELOPMENT_SHARE,
                "Présence séance " + session.getSessionDate(), MovementOrigin.presence,
                session, toEntity(member), attendance.getId(), user);

        } else if (amountPaid.compareTo(new BigDecimal("4000")) >= 0) {
            // Repay both advances; current session covered by fund
            recoverAdvance(oldest, fund);
            recoverAdvance(secondOldest, fund);
            if (fund.hasSufficientBalance(PRESENCE_FEE)) {
                advanceFund(fund, session, PRESENCE_FEE);
                createSanction(member, session, SANCTION_FUND_AMT,
                               "Cotisé par le fond (séance tenante)", SanctionOrigin.presence_fund,
                               attendance.getId());
                attendance.setCoveredByFund(true);
                attendance.setAttendanceStatus(AttendanceStatus.covered_by_fund);
                result.setStatus(AttendanceStatus.covered_by_fund);
                result.setContributionToTontine(TONTINE_SHARE);
                result.setContributionToBeverage(BEVERAGE_SHARE);
                result.setContributionToDevelopment(DEVELOPMENT_SHARE);
                cashboxService.credit(CashboxType.development, DEVELOPMENT_SHARE,
                    "Présence (fond) séance " + session.getSessionDate(), MovementOrigin.presence,                    session, toEntity(member), attendance.getId(), user);

            } else {
                processDefault(member, session, attendance, result, user);
            }

        } else if (amountPaid.compareTo(PRESENCE_FEE) >= 0) {
            // Repay only the oldest; second advance still pending + fund covers current
            recoverAdvance(oldest, fund);
            if (fund.hasSufficientBalance(PRESENCE_FEE)) {
                advanceFund(fund, session, PRESENCE_FEE);
                createSanction(member, session, SANCTION_FUND_AMT,
                               "Cotisé par le fond (séance tenante)", SanctionOrigin.presence_fund,
                               attendance.getId());
                attendance.setCoveredByFund(true);
                attendance.setAttendanceStatus(AttendanceStatus.covered_by_fund);
                result.setStatus(AttendanceStatus.covered_by_fund);
                result.setContributionToTontine(TONTINE_SHARE);
                result.setContributionToBeverage(BEVERAGE_SHARE);
                result.setContributionToDevelopment(DEVELOPMENT_SHARE);
                cashboxService.credit(CashboxType.development, DEVELOPMENT_SHARE,
                    "Présence (fond) séance " + session.getSessionDate(), MovementOrigin.presence,                    session, toEntity(member), attendance.getId(), user);

            } else {
                processDefault(member, session, attendance, result, user);
            }

        } else if (amountPaid.compareTo(PARTIAL_FUND_AMT) == 0) {
            // Compromis: 1 000 FCFA — fund is exhausted
            fund.setBalance(BigDecimal.ZERO);
            entityManager.merge(fund);
            attendance.setAttendanceStatus(AttendanceStatus.covered_by_fund);
            result.setStatus(AttendanceStatus.covered_by_fund);
            result.setPartialFund(true);
            // Partial contributions (only 1 000 paid)
            result.setContributionToTontine(new BigDecimal("500"));
            result.setContributionToBeverage(new BigDecimal("250"));
            result.setContributionToDevelopment(new BigDecimal("250"));

        } else {
            // Full default
            processDefault(member, session, attendance, result, user);
        }

        return result;
    }

    // ── Default ──────────────────────────────────────────────────────────────

    private void processDefault(Member member, MeetingSessionEntity session,
                                 SessionAttendanceEntity attendance, AttendanceResult result,
                                 AppUser user) {
        attendance.setAttendanceStatus(AttendanceStatus.default_status);
        result.setStatus(AttendanceStatus.default_status);
        result.setContributionToTontine(BigDecimal.ZERO);
        result.setContributionToBeverage(BigDecimal.ZERO);
        result.setContributionToDevelopment(BigDecimal.ZERO);
        createSanction(member, session, SANCTION_DEFAULT_AMT,
                       "Échec de cotisation de présence", SanctionOrigin.presence_default,
                       attendance.getId());
    }

    // ── Fund helpers ─────────────────────────────────────────────────────────

    private void advanceFund(RevolvingFundEntity fund, MeetingSessionEntity session, BigDecimal amount) {
        fund.setBalance(fund.getBalance().subtract(amount));
        entityManager.merge(fund);

        RevolvingFundMovementEntity mv = new RevolvingFundMovementEntity();
        mv.setFund(fund);
        mv.setSession(session);
        mv.setMovementType(FundMovementType.advance);
        mv.setAmount(amount);
        mv.setRecovered(false);
        entityManager.persist(mv);
    }

    private void recoverAdvance(RevolvingFundMovementEntity advance, RevolvingFundEntity fund) {
        advance.setRecovered(true);
        entityManager.merge(advance);
        fund.setBalance(fund.getBalance().add(advance.getAmount()));
        entityManager.merge(fund);
    }

    private void recordRepayment(RevolvingFundEntity fund, MeetingSessionEntity session, BigDecimal amount) {
        RevolvingFundMovementEntity mv = new RevolvingFundMovementEntity();
        mv.setFund(fund);
        mv.setSession(session);
        mv.setMovementType(FundMovementType.repayment);
        mv.setAmount(amount);
        mv.setRecovered(true);
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

    // ── Result DTO ───────────────────────────────────────────────────────────

    @lombok.Getter @lombok.Setter
    public static class AttendanceResult {
        private final Member member;
        private AttendanceStatus status;
        private BigDecimal contributionToTontine    = BigDecimal.ZERO;
        private BigDecimal contributionToBeverage   = BigDecimal.ZERO;
        private BigDecimal contributionToDevelopment = BigDecimal.ZERO;
        private boolean    partialFund              = false;

        public AttendanceResult(Member member) { this.member = member; }

        public boolean isDefault()     { return status == AttendanceStatus.default_status; }
        public boolean isCoveredByFund(){ return status == AttendanceStatus.covered_by_fund; }
        public boolean isUpToDate()    { return status == AttendanceStatus.up_to_date; }
    }
}
