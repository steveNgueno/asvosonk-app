package org.asvosonk.cashbox.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.cashbox.domain.model.Cashbox;
import org.asvosonk.cashbox.domain.repository.CashboxRepository;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Use case: generate a balance report for all cashboxes.
 */
@Service
@RequiredArgsConstructor
public class GenerateBalanceUseCase {

    private final CashboxRepository cashboxRepository;

    /**
     * Returns the current balance for all cashbox types.
     */
    @Transactional(readOnly = true)
    public Map<CashboxType, BigDecimal> execute() {
        return Arrays.stream(CashboxType.values())
            .collect(Collectors.toMap(
                type -> type,
                type -> cashboxRepository.findByType(type)
                    .map(Cashbox::getBalance)
                    .orElse(BigDecimal.ZERO)
            ));
    }

    /**
     * Returns the current balance for a specific cashbox type.
     */
    @Transactional(readOnly = true)
    public BigDecimal execute(CashboxType type) {
        return cashboxRepository.findByType(type)
            .map(Cashbox::getBalance)
            .orElse(BigDecimal.ZERO);
    }
}
