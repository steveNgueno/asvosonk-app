package org.asvosonk.session.domain.model;

import org.asvosonk.session.domain.valueobject.FundMovementType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pure domain model for a revolving fund movement.
 * All JPA concerns are handled by RevolvingFundMovementEntity in the infrastructure layer.
 */
public class RevolvingFundMovement {

    private final Long id;
    private final Long fundId;
    private final Long sessionId;
    private final FundMovementType movementType;
    private final BigDecimal amount;
    private final boolean recovered;
    private final LocalDateTime createdAt;

    public RevolvingFundMovement(Long id, Long fundId, Long sessionId,
                                 FundMovementType movementType, BigDecimal amount,
                                 boolean recovered, LocalDateTime createdAt) {
        this.id = id;
        this.fundId = fundId;
        this.sessionId = sessionId;
        this.movementType = movementType;
        this.amount = amount;
        this.recovered = recovered;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getFundId() { return fundId; }
    public Long getSessionId() { return sessionId; }
    public FundMovementType getMovementType() { return movementType; }
    public BigDecimal getAmount() { return amount; }
    public boolean isRecovered() { return recovered; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
