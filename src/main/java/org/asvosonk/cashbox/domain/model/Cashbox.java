package org.asvosonk.cashbox.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pure domain model for a cashbox.
 * All JPA concerns are handled by CashboxEntity in the infrastructure layer.
 */
@Getter
@AllArgsConstructor
public class Cashbox {

    private final Integer id;
    private final CashboxType type;
    private BigDecimal balance;
    private LocalDateTime updatedAt;

}
