package org.asvosonk.cashbox.domain.repository;

import org.asvosonk.cashbox.domain.model.CashboxMovement;

import java.util.List;

/**
 * Domain repository port for CashboxMovement.
 * Infrastructure layer provides the JPA implementation.
 */
public interface CashboxMovementRepository {

    List<CashboxMovement> findBySessionIdOrderByMovementDateAsc(Long sessionId);

    List<CashboxMovement> findByCashboxIdOrderByMovementDateDesc(Integer cashboxId);

    List<CashboxMovement> findRecentMovements(int limit);

    List<CashboxMovement> findAllByOrderByMovementDateDesc();

    CashboxMovement save(CashboxMovement movement);
}
