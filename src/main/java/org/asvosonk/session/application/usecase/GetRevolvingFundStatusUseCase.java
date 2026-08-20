package org.asvosonk.session.application.usecase;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.asvosonk.session.domain.valueobject.FundMovementType;
import org.asvosonk.session.domain.valueobject.AttendanceStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read model for the "situation fond de roulement" column of the attendance
 * screen: current balance and number of still-unrecovered advances per member.
 *
 * <p>The query used to sit inline in {@code SessionController}, which made the
 * presentation layer talk JPQL to the database directly. It is grouped here so
 * the controller only consumes a typed read model.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetRevolvingFundStatusUseCase {

    private final EntityManager entityManager;

    /** Solde du fond et nombre d'avances non remboursées pour un membre. */
    public record FundStatus(BigDecimal balance, int pendingAdvances) {

        public boolean isUpToDate()  { return pendingAdvances == 0; }
        public boolean isOneDue()    { return pendingAdvances == 1; }
        public boolean isSeveralDue(){ return pendingAdvances >= 2; }
    }

    /**
     * Nombre d'échecs de cotisation encore dus par membre : ces séances restent
     * recouvrables, en espèces ou par le fond une fois rechargé.
     */
    public Map<Long, Long> openFailuresByMember() {
        // Le statut est un ENUM PostgreSQL : il se compare par paramètre lié.
        // Un littéral d'énumération Java serait rendu 'default_status'::AttendanceStatus,
        // un type qui n'existe pas en base.
        List<Object[]> rows = entityManager.createQuery("""
                SELECT a.member.id, COUNT(a)
                  FROM SessionAttendanceEntity a
                 WHERE a.attendanceStatus = :failed
                   AND a.recoveredSession IS NULL
                 GROUP BY a.member.id
                """, Object[].class)
            .setParameter("failed", AttendanceStatus.default_status)
            .getResultList();

        Map<Long, Long> failures = new HashMap<>();
        for (Object[] row : rows) {
            failures.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return failures;
    }

    /** Une dette antérieure : avance du fond à rembourser, ou séance en échec. */
    public record OutstandingDebt(LocalDate sessionDate, BigDecimal amount, boolean advance) {

        /** Forme compacte lue par la feuille de présence : {@code a2000} ou {@code f1000}. */
        public String token() {
            return (advance ? "a" : "f") + amount.toBigInteger();
        }
    }

    /**
     * Dettes encore dues par membre, de la plus ancienne à la plus récente —
     * exactement l'ordre dans lequel la clôture les impute.
     *
     * <p>Sert à l'aide à la saisie de la feuille de présence : sans le détail des
     * montants, l'écran ne pourrait qu'annoncer « le serveur décidera ».</p>
     */
    public Map<Long, List<OutstandingDebt>> outstandingDebtsByMember() {
        Map<Long, List<OutstandingDebt>> debts = new HashMap<>();

        List<Object[]> advances = entityManager.createQuery("""
                SELECT m.fund.member.id, m.session.sessionDate, m.amount
                  FROM RevolvingFundMovementEntity m
                 WHERE m.movementType = :advance
                   AND m.recovered = false
                """, Object[].class)
            .setParameter("advance", FundMovementType.advance)
            .getResultList();
        for (Object[] row : advances) {
            debts.computeIfAbsent((Long) row[0], k -> new ArrayList<>())
                .add(new OutstandingDebt((LocalDate) row[1], (BigDecimal) row[2], true));
        }

        List<Object[]> failures = entityManager.createQuery("""
                SELECT a.member.id, a.session.sessionDate, a.amountDue
                  FROM SessionAttendanceEntity a
                 WHERE a.attendanceStatus = :failed
                   AND a.recoveredSession IS NULL
                """, Object[].class)
            .setParameter("failed", AttendanceStatus.default_status)
            .getResultList();
        for (Object[] row : failures) {
            debts.computeIfAbsent((Long) row[0], k -> new ArrayList<>())
                .add(new OutstandingDebt((LocalDate) row[1], (BigDecimal) row[2], false));
        }

        debts.values().forEach(list -> list.sort(Comparator.comparing(OutstandingDebt::sessionDate)));
        return debts;
    }

    /** Les dettes d'un membre sous la forme lue par le script : {@code a2000,f1000}. */
    public static String debtTokens(List<OutstandingDebt> debts) {
        if (debts == null || debts.isEmpty()) {
            return "";
        }
        return debts.stream().map(OutstandingDebt::token).collect(Collectors.joining(","));
    }

    /**
     * Fund status of every member that has a revolving fund, keyed by member id.
     * Members without a fund row are simply absent from the map.
     */
    public Map<Long, FundStatus> findAllByMemberId() {
        // Le type d'un mouvement est un ENUM PostgreSQL (fund_movement_type) :
        // il est comparé via un paramètre lié, et non via un littéral d'énumération
        // Java — Hibernate le rendrait en 'advance'::FundMovementType, un type
        // inexistant côté base.
        List<Object[]> rows = entityManager.createQuery("""
                SELECT f.member.id, f.balance,
                       (SELECT COUNT(m) FROM RevolvingFundMovementEntity m
                         WHERE m.fund.member.id = f.member.id
                           AND m.movementType = :advance
                           AND m.recovered = false)
                  FROM RevolvingFundEntity f
                """, Object[].class)
            .setParameter("advance", FundMovementType.advance)
            .getResultList();

        Map<Long, FundStatus> statuses = new HashMap<>();
        for (Object[] row : rows) {
            statuses.put((Long) row[0],
                new FundStatus((BigDecimal) row[1], ((Number) row[2]).intValue()));
        }
        return statuses;
    }
}
