package org.asvosonk.cashbox.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.asvosonk.cashbox.domain.model.CashboxMovement;
import org.asvosonk.cashbox.domain.repository.CashboxMovementRepository;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;
import org.asvosonk.cashbox.infrastructure.persistence.entity.CashboxMovementEntity;
import org.asvosonk.cashbox.infrastructure.persistence.mapper.CashboxMovementMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Primary
@Repository
@RequiredArgsConstructor
public class JpaCashboxMovementRepository implements CashboxMovementRepository {

    private final SpringDataCashboxMovementRepository springData;
    private final jakarta.persistence.EntityManager  entityManager;

    @Override
    public List<CashboxMovement> findBySessionIdOrderByMovementDateAsc(Long sessionId) {
        return springData.findBySessionIdOrderByMovementDateAsc(sessionId).stream()
            .map(CashboxMovementMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<CashboxMovement> findByCashboxIdOrderByMovementDateDesc(Integer cashboxId) {
        return springData.findByCashboxIdOrderByMovementDateDesc(cashboxId).stream()
            .map(CashboxMovementMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<CashboxMovement> findRecentMovements(int limit) {
        return springData.findRecent(
                org.springframework.data.domain.PageRequest.of(0, Math.max(1, limit))).stream()
            .map(CashboxMovementMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<CashboxMovement> findAllByOrderByMovementDateDesc() {
        return springData.findAllByOrderByMovementDateDesc().stream()
            .map(CashboxMovementMapper::toDomain)
            .collect(Collectors.toList());
    }

    /**
     * Filtered movement history, built with the Criteria API.
     *
     * <p>The previous implementation used a single JPQL query with
     * {@code (:type IS NULL OR ...)} predicates. PostgreSQL cannot infer the type
     * of a parameter that only appears in a bare {@code ? IS NULL} position, so
     * the query failed with <em>"could not determine data type of parameter $1"</em>
     * — the movement history page returned HTTP 500 on every visit, filters or
     * not. Composing the predicates only when a filter is actually provided
     * removes the untyped parameter entirely.
     *
     * <p>Date bounds stay inclusive: {@code dateTo} covers the whole day, and the
     * comparison is done on the raw timestamp (no {@code CAST} on the column), so
     * an index on {@code movement_date} remains usable.
     */
    @Override
    public List<CashboxMovement> findFiltered(CashboxType type, LocalDate dateFrom, LocalDate dateTo) {
        var cb = entityManager.getCriteriaBuilder();
        var query = cb.createQuery(CashboxMovementEntity.class);
        var movement = query.from(CashboxMovementEntity.class);
        movement.fetch("cashbox");

        var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
        if (type != null) {
            predicates.add(cb.equal(movement.get("cashbox").get("type"), type));
        }
        if (dateFrom != null) {
            predicates.add(cb.greaterThanOrEqualTo(
                movement.get("movementDate"), dateFrom.atStartOfDay()));
        }
        if (dateTo != null) {
            predicates.add(cb.lessThan(
                movement.get("movementDate"), dateTo.plusDays(1).atStartOfDay()));
        }

        query.select(movement)
            .where(cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new)))
            .orderBy(cb.desc(movement.get("movementDate")));

        return entityManager.createQuery(query).getResultList().stream()
            .map(CashboxMovementMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public CashboxMovement save(CashboxMovement movement) {
        // For full entity mapping, the caller should use entityManager directly
        // This saves a partially mapped entity
        CashboxMovementEntity entity = CashboxMovementMapper.toEntity(movement);
        if (entity != null && movement.getId() != null) {
            entity.setId(movement.getId());
        }
        CashboxMovementEntity saved = springData.save(entity);
        return CashboxMovementMapper.toDomain(saved);
    }

}
