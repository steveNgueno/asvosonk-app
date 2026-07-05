package org.asvosonk.session.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pure domain model for a member's revolving fund balance.
 * All JPA concerns are handled by RevolvingFundEntity in the infrastructure layer.
 */
@Getter
@AllArgsConstructor
public class RevolvingFund {

    private final Long id;
    private final Long memberId;
    private BigDecimal balance;
    private LocalDateTime updatedAt;


    public boolean hasSufficientBalance(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }
}
