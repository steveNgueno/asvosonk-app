package org.asvosonk.cashbox.infrastructure.persistence.repository;

import org.asvosonk.cashbox.infrastructure.persistence.entity.CashboxMovementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataCashboxMovementRepository extends JpaRepository<CashboxMovementEntity, Long> {
    List<CashboxMovementEntity> findBySessionIdOrderByMovementDateAsc(Long sessionId);
    List<CashboxMovementEntity> findByCashboxIdOrderByMovementDateDesc(Integer cashboxId);
    List<CashboxMovementEntity> findTop10ByOrderByMovementDateDesc();
    List<CashboxMovementEntity> findAllByOrderByMovementDateDesc();
}
