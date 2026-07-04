package org.asvosonk.cashbox.domain.model;

import org.asvosonk.cashbox.domain.valueobject.CashboxType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pure domain model for a cashbox.
 * All JPA concerns are handled by CashboxEntity in the infrastructure layer.
 */
public class Cashbox {

    private final Integer id;
    private final CashboxType type;
    private BigDecimal balance;
    private LocalDateTime updatedAt;

    public Cashbox(Integer id, CashboxType type, BigDecimal balance, LocalDateTime updatedAt) {
        this.id = id;
        this.type = type;
        this.balance = balance;
        this.updatedAt = updatedAt;
    }

    public Integer getId() { return id; }
    public CashboxType getType() { return type; }
    public BigDecimal getBalance() { return balance; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
