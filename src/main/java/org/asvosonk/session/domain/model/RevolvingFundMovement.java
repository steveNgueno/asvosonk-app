package org.asvosonk.session.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.asvosonk.session.domain.valueobject.FundMovementType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pure domain model for a revolving fund movement.
 * All JPA concerns are handled by RevolvingFundMovementEntity in the infrastructure layer.
 */
@Getter
@Setter
@AllArgsConstructor
public class RevolvingFundMovement {

    private final Long id;
    private final Long fundId;
    private final Long sessionId;
    private final FundMovementType movementType;
    private final BigDecimal amount;
    private final boolean recovered;
    private final LocalDateTime createdAt;

}
