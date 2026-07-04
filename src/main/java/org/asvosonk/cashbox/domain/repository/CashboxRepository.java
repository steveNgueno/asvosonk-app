package org.asvosonk.cashbox.domain.repository;

import org.asvosonk.cashbox.domain.model.Cashbox;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;

import java.util.Optional;

/**
 * Domain repository port for Cashbox.
 * Infrastructure layer provides the JPA implementation.
 */
public interface CashboxRepository {

    Optional<Cashbox> findByType(CashboxType type);

    Cashbox save(Cashbox cashbox);
}
