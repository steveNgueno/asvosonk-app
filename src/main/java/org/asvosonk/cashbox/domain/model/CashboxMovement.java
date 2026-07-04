package org.asvosonk.cashbox.domain.model;

import org.asvosonk.cashbox.domain.valueobject.MovementDirection;
import org.asvosonk.cashbox.domain.valueobject.MovementOrigin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pure domain model for a cashbox movement (transaction).
 * All JPA concerns are handled by CashboxMovementEntity in the infrastructure layer.
 */
public class CashboxMovement {

    private final Long id;
    private final Cashbox cashbox;
    private final LocalDateTime movementDate;
    private final MovementDirection direction;
    private final BigDecimal amount;
    private final String reason;
    private final MovementOrigin origin;
    private final Long memberId;
    private final Long sessionId;
    private final Long referenceId;
    private final Long createdByUserId;
    private final LocalDateTime createdAt;

    public CashboxMovement(Long id, Cashbox cashbox, LocalDateTime movementDate,
                           MovementDirection direction, BigDecimal amount, String reason,
                           MovementOrigin origin, Long memberId, Long sessionId,
                           Long referenceId, Long createdByUserId, LocalDateTime createdAt) {
        this.id = id;
        this.cashbox = cashbox;
        this.movementDate = movementDate;
        this.direction = direction;
        this.amount = amount;
        this.reason = reason;
        this.origin = origin;
        this.memberId = memberId;
        this.sessionId = sessionId;
        this.referenceId = referenceId;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Cashbox getCashbox() { return cashbox; }
    public LocalDateTime getMovementDate() { return movementDate; }
    public MovementDirection getDirection() { return direction; }
    public BigDecimal getAmount() { return amount; }
    public String getReason() { return reason; }
    public MovementOrigin getOrigin() { return origin; }
    public Long getMemberId() { return memberId; }
    public Long getSessionId() { return sessionId; }
    public Long getReferenceId() { return referenceId; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
