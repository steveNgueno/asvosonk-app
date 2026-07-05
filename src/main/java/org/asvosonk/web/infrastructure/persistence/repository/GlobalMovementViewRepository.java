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

    @SuppressWarnings("unchecked")
    public List<GlobalMovementView> search(Long memberId, String module,
                                           LocalDate dateFrom, LocalDate dateTo) {
        String sql = """
            SELECT * FROM global_movement_view
            WHERE (:memberId IS NULL OR member_id = :memberId)
              AND (:module IS NULL OR module = :module)
              AND (:dateFrom IS NULL OR event_date >= :dateFrom)
              AND (:dateTo IS NULL OR event_date <= :dateTo)
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
        String sql = """
            SELECT * FROM global_movement_view
            WHERE (:memberId IS NULL OR member_id = :memberId)
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
        return rows.stream().map(row -> new GlobalMovementView(
            row[0] != null ? ((Number) row[0]).longValue() : null,
            (String) row[1],
            row[2] != null ? ((Number) row[2]).longValue() : null,
            row[3] != null ? ((java.sql.Date) row[3]).toLocalDate() : null,
            row[4] != null ? ((Number) row[4]).doubleValue() != 0 ? BigDecimal.valueOf(((Number) row[4]).doubleValue()) : BigDecimal.ZERO : null,
            (String) row[5]
        )).toList();
    }
}
