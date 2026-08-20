package org.asvosonk.web.infrastructure.persistence.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.asvosonk.web.infrastructure.persistence.entity.GlobalMovementView;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Repository for querying the global_movement_view using native SQL.
 * Does NOT use JPA entity mapping because reference_id is not unique across UNION ALL modules.
 */
@Repository
@RequiredArgsConstructor
public class GlobalMovementViewRepository {

    private final EntityManager entityManager;

    /**
     * Filtered movement search (module / member / period).
     *
     * <p>Every optional clause is CAST to its SQL type. Without the cast,
     * PostgreSQL cannot infer the type of a parameter appearing only in a bare
     * {@code ? IS NULL} position and rejects the whole statement with
     * <em>"could not determine data type of parameter"</em> — the filter mode of
     * the global search failed with an HTTP 500 on every call (same root cause
     * already worked around in {@link #searchByKeyword}).
     */
    @SuppressWarnings("unchecked")
    public List<GlobalMovementView> search(Long memberId, String module,
                                           LocalDate dateFrom, LocalDate dateTo) {
        String sql = """
            SELECT * FROM global_movement_view
            WHERE (CAST(:memberId AS bigint) IS NULL OR member_id = CAST(:memberId AS bigint))
              AND (CAST(:module   AS text)   IS NULL OR module    = CAST(:module   AS text))
              AND (CAST(:dateFrom AS date)   IS NULL OR event_date >= CAST(:dateFrom AS date))
              AND (CAST(:dateTo   AS date)   IS NULL OR event_date <= CAST(:dateTo   AS date))
            ORDER BY event_date DESC
            LIMIT 200
            """;
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("memberId", memberId);
        query.setParameter("module", module);
        query.setParameter("dateFrom", dateFrom);
        query.setParameter("dateTo", dateTo);
        return mapResults(query.getResultList());
    }

    @SuppressWarnings("unchecked")
    public List<GlobalMovementView> searchByKeyword(Long memberId, String keyword) {
        // F-17: the member clause must only contribute when an id is actually
        // provided. The previous `(:memberId IS NULL OR member_id = :memberId) OR ...`
        // made the whole predicate TRUE for the common text-search case
        // (memberId null), returning every recent movement instead of matches.
        // `:memberId IS NOT NULL AND ...` keeps the numeric cross-reference
        // (a member's rows still surface) without swallowing the keyword filter.
        // CAST(... AS bigint) is required: the named parameter appears in a
        // bare `IS NOT NULL` position where PostgreSQL cannot infer its type
        // ("could not determine data type of parameter").
        String sql = """
            SELECT * FROM global_movement_view
            WHERE (CAST(:memberId AS bigint) IS NOT NULL AND member_id = :memberId)
               OR module ILIKE :keyword
               OR status ILIKE :keyword
            ORDER BY event_date DESC
            LIMIT 200
            """;
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("memberId", memberId);
        query.setParameter("keyword", keyword);
        return mapResults(query.getResultList());
    }

    @SuppressWarnings("unchecked")
    private List<GlobalMovementView> mapResults(List<Object[]> rows) {
        // View column order is: module, reference_id, member_id, event_date,
        // amount, status. The constructor takes (referenceId, module, memberId,
        // ...), so referenceId=row[1] and module=row[0] — they must NOT be
        // read as row[0]/row[1] respectively (that threw ClassCastException on
        // any non-empty result: module String read as a Number).
        return rows.stream().map(row -> new GlobalMovementView(
            row[1] != null ? ((Number) row[1]).longValue() : null,
            (String) row[0],
            row[2] != null ? ((Number) row[2]).longValue() : null,
            row[3] != null ? toLocalDate(row[3]) : null,
            row[4] != null ? toBigDecimal(row[4]) : null,
            (String) row[5]
        )).toList();
    }

    /**
     * F-41 — money must stay exact end to end. Routing the native-query result
     * through double (BigDecimal.valueOf(Number.doubleValue())) can lose or
     * distort cents; going through the driver's own decimal string never does.
     */
    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(value.toString());
    }

    /**
     * Safely converts a raw query result to LocalDate, handling both
     * java.sql.Date (older JDBC driver) and java.time.LocalDate (PG 42.x+).
     */
    private java.time.LocalDate toLocalDate(Object value) {
        if (value instanceof java.time.LocalDate) {
            return (java.time.LocalDate) value;
        }
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate();
        }
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toLocalDateTime().toLocalDate();
        }
        throw new IllegalArgumentException(
            "Cannot convert " + value.getClass().getName() + " to LocalDate: " + value);
    }
}
