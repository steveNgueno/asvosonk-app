package org.asvosonk.cashbox.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.asvosonk.cashbox.domain.model.CashboxMovement;
import org.asvosonk.cashbox.domain.repository.CashboxMovementRepository;
import org.asvosonk.cashbox.infrastructure.persistence.entity.CashboxMovementEntity;
import org.asvosonk.cashbox.infrastructure.persistence.mapper.CashboxMovementMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Primary
@Repository
@RequiredArgsConstructor
public class JpaCashboxMovementRepository implements CashboxMovementRepository {

    private final SpringDataCashboxMovementRepository springData;

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
        return springData.findTop10ByOrderByMovementDateDesc().stream()
            .limit(limit)
            .map(CashboxMovementMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<CashboxMovement> findAllByOrderByMovementDateDesc() {
        return springData.findAllByOrderByMovementDateDesc().stream()
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

    @org.springframework.stereotype.Repository
    interface SpringDataCashboxMovementRepository extends JpaRepository<CashboxMovementEntity, Long> {
        List<CashboxMovementEntity> findBySessionIdOrderByMovementDateAsc(Long sessionId);
        List<CashboxMovementEntity> findByCashboxIdOrderByMovementDateDesc(Integer cashboxId);
        List<CashboxMovementEntity> findTop10ByOrderByMovementDateDesc();
        List<CashboxMovementEntity> findAllByOrderByMovementDateDesc();
    }
}
