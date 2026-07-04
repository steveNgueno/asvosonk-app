package org.asvosonk.session.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pure domain model for a member's revolving fund balance.
 * All JPA concerns are handled by RevolvingFundEntity in the infrastructure layer.
 */
public class RevolvingFund {

    private final Long id;
    private final Long memberId;
    private BigDecimal balance;
    private LocalDateTime updatedAt;

    public RevolvingFund(Long id, Long memberId, BigDecimal balance, LocalDateTime updatedAt) {
        this.id = id;
        this.memberId = memberId;
        this.balance = balance;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public BigDecimal getBalance() { return balance; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public boolean hasSufficientBalance(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }
}
