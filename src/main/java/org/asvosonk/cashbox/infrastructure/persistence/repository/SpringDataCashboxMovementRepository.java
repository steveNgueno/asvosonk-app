package org.asvosonk.cashbox.infrastructure.persistence.repository;

import org.asvosonk.cashbox.infrastructure.persistence.entity.CashboxMovementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataCashboxMovementRepository extends JpaRepository<CashboxMovementEntity, Long> {

    @Query("SELECT m FROM CashboxMovementEntity m JOIN FETCH m.cashbox WHERE m.session.id = :sessionId ORDER BY m.movementDate ASC")
    List<CashboxMovementEntity> findBySessionIdOrderByMovementDateAsc(Long sessionId);

    @Query("SELECT m FROM CashboxMovementEntity m JOIN FETCH m.cashbox WHERE m.cashbox.id = :cashboxId ORDER BY m.movementDate DESC")
    List<CashboxMovementEntity> findByCashboxIdOrderByMovementDateDesc(Integer cashboxId);

    @Query("SELECT m FROM CashboxMovementEntity m JOIN FETCH m.cashbox ORDER BY m.movementDate DESC")
    List<CashboxMovementEntity> findTop10ByOrderByMovementDateDesc();

    @Query("SELECT m FROM CashboxMovementEntity m JOIN FETCH m.cashbox ORDER BY m.movementDate DESC")
    List<CashboxMovementEntity> findAllByOrderByMovementDateDesc();
}
