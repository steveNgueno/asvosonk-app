package org.asvosonk.sanction.domain.model;

import org.asvosonk.sanction.domain.valueobject.SanctionOrigin;
import org.asvosonk.sanction.domain.valueobject.SanctionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Pure domain model for a sanction.
 * All JPA concerns are handled by SanctionEntity in the infrastructure layer.
 */
public class Sanction {

    private final Long id;
    private final Long memberId;
    private LocalDate sanctionDate;
    private BigDecimal amount;
    private BigDecimal amountPaid;
    private String reason;
    private SanctionOrigin origin;
    private Long referenceId;
    private SanctionStatus status;
    private LocalDate paymentDate;
    private String cancelReason;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Sanction(Long id, Long memberId, LocalDate sanctionDate, BigDecimal amount,
                    String reason, SanctionOrigin origin, Long referenceId,
                    SanctionStatus status, LocalDate paymentDate, String cancelReason,
                    LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, memberId, sanctionDate, amount,
             status == SanctionStatus.paid ? amount : BigDecimal.ZERO,
             reason, origin, referenceId, status, paymentDate, cancelReason,
             createdAt, updatedAt);
    }

    public Sanction(Long id, Long memberId, LocalDate sanctionDate, BigDecimal amount,
                    BigDecimal amountPaid, String reason, SanctionOrigin origin,
                    Long referenceId, SanctionStatus status, LocalDate paymentDate,
                    String cancelReason, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.memberId = memberId;
        this.sanctionDate = sanctionDate;
        this.amount = amount;
        this.amountPaid = amountPaid != null ? amountPaid : BigDecimal.ZERO;
        this.reason = reason;
        this.origin = origin;
        this.referenceId = referenceId;
        this.status = status;
        this.paymentDate = paymentDate;
        this.cancelReason = cancelReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public LocalDate getSanctionDate() { return sanctionDate; }
    public BigDecimal getAmount() { return amount; }
    public String getReason() { return reason; }
    public SanctionOrigin getOrigin() { return origin; }
    public Long getReferenceId() { return referenceId; }
    public SanctionStatus getStatus() { return status; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public String getCancelReason() { return cancelReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public BigDecimal getAmountPaid() { return amountPaid; }

    public boolean isUnpaid() { return status == SanctionStatus.unpaid; }

    /** Reste à payer : une retenue sur tontine peut n'avoir couvert qu'une partie. */
    public BigDecimal getRemaining() {
        return amount.subtract(amountPaid).max(BigDecimal.ZERO);
    }

    /** Vrai si la sanction a été partiellement encaissée sans être soldée. */
    public boolean isPartiallyPaid() {
        return isUnpaid() && amountPaid.signum() > 0;
    }
}
