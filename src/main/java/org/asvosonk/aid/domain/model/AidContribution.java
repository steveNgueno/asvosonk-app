package org.asvosonk.aid.domain.model;

import org.asvosonk.aid.domain.valueobject.AidContributionStatus;
import org.asvosonk.aid.domain.valueobject.AidPaymentMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Pure domain model for an aid contribution (la part d'un membre sur une aide).
 * All JPA concerns are handled by AidContributionEntity in the infrastructure layer.
 */
public class AidContribution {

    private final Long id;
    private final Long aidId;
    private final Long memberId;
    private final BigDecimal amountDue;
    private BigDecimal amountPaid;
    private AidContributionStatus status;
    private AidPaymentMode paymentMode;
    private LocalDate paymentDate;
    /** Séance au cours de laquelle la part a été recouverte. */
    private final Long sessionId;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AidContribution(Long id, Long aidId, Long memberId,
                           BigDecimal amountDue, BigDecimal amountPaid,
                           AidContributionStatus status, AidPaymentMode paymentMode,
                           LocalDate paymentDate, Long sessionId,
                           LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.aidId = aidId;
        this.memberId = memberId;
        this.amountDue = amountDue != null ? amountDue : BigDecimal.ZERO;
        this.amountPaid = amountPaid != null ? amountPaid : BigDecimal.ZERO;
        this.status = status != null ? status : AidContributionStatus.owed;
        this.paymentMode = paymentMode;
        this.paymentDate = paymentDate;
        this.sessionId = sessionId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public Long getAidId() { return aidId; }
    public Long getMemberId() { return memberId; }
    public BigDecimal getAmountDue() { return amountDue; }
    public BigDecimal getAmountPaid() { return amountPaid; }
    public AidContributionStatus getStatus() { return status; }
    public AidPaymentMode getPaymentMode() { return paymentMode; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public Long getSessionId() { return sessionId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    /** Reste à recouvrir sur cette part (une retenue peut n'être que partielle). */
    public BigDecimal getRemaining() {
        return amountDue.subtract(amountPaid).max(BigDecimal.ZERO);
    }

    public boolean isOwed() { return status == AidContributionStatus.owed; }

    /**
     * Encaisse {@code collected} sur cette part et la solde si elle est
     * entièrement couverte.
     *
     * @return le montant réellement encaissé
     */
    public BigDecimal collect(BigDecimal collected, AidPaymentMode mode, LocalDate paidOn) {
        BigDecimal taken = collected.min(getRemaining()).max(BigDecimal.ZERO);
        if (taken.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        this.amountPaid = this.amountPaid.add(taken);
        if (this.amountPaid.compareTo(this.amountDue) >= 0) {
            this.amountPaid = this.amountDue;
            this.status = AidContributionStatus.paid;
            this.paymentDate = paidOn;
        }
        this.paymentMode = mode;
        return taken;
    }
}
