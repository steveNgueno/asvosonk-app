package org.asvosonk.cashbox.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.asvosonk.cashbox.domain.valueobject.MovementDirection;
import org.asvosonk.cashbox.domain.valueobject.MovementOrigin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pure domain model for a cashbox movement (transaction).
 * All JPA concerns are handled by CashboxMovementEntity in the infrastructure layer.
 */
@Getter
@AllArgsConstructor
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

}
