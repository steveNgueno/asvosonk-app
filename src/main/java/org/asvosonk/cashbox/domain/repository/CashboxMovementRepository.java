package org.asvosonk.cashbox.domain.repository;

import org.asvosonk.cashbox.domain.model.CashboxMovement;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;

import java.time.LocalDate;
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

    /** F-48 — filter directly in SQL (type/date range) instead of loading everything into memory. */
    List<CashboxMovement> findFiltered(CashboxType type, LocalDate dateFrom, LocalDate dateTo);

    CashboxMovement save(CashboxMovement movement);
}
