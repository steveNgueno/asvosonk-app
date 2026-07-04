package org.asvosonk.cashbox.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.cashbox.domain.model.Cashbox;
import org.asvosonk.cashbox.domain.repository.CashboxRepository;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Use case: close or balance a cashbox.
 * Validates that the cashbox exists and returns its final balance.
 */
@Service
@RequiredArgsConstructor
public class CloseCashboxUseCase {

    private final CashboxRepository cashboxRepository;

    /**
     * Returns the final balance of a cashbox before closing.
     * In a real scenario this would also archive movements and mark as closed.
     */
    @Transactional(readOnly = true)
    public BigDecimal execute(CashboxType type) {
        Cashbox cashbox = cashboxRepository.findByType(type)
            .orElseThrow(() -> new IllegalStateException("Cashbox not found: " + type));
        return cashbox.getBalance();
    }
}
