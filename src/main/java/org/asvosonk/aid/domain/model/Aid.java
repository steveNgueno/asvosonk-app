package org.asvosonk.aid.domain.model;

import org.asvosonk.aid.domain.valueobject.AidStatus;
import org.asvosonk.aid.domain.valueobject.AidType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Pure domain model for an aid (assistance sociale).
 * All JPA concerns are handled by AidEntity in the infrastructure layer.
 */
public class Aid {

    private final Long id;
    private final Long beneficiaryId;
    private AidType type;
    private LocalDate aidDate;
    private String description;
    private BigDecimal totalAmount;
    private BigDecimal sharePerMember;
    private AidStatus status;
    /** Séance au cours de laquelle l'aide a été enregistrée. */
    private final Long sessionId;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Aid(Long id, Long beneficiaryId, AidType type, LocalDate aidDate,
               String description, BigDecimal totalAmount, BigDecimal sharePerMember,
               AidStatus status, Long sessionId, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.beneficiaryId = beneficiaryId;
        this.type = type;
        this.aidDate = aidDate;
        this.description = description;
        this.totalAmount = totalAmount;
        this.sharePerMember = sharePerMember != null ? sharePerMember : BigDecimal.ZERO;
        this.status = status != null ? status : AidStatus.in_progress;
        this.sessionId = sessionId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public Long getBeneficiaryId() { return beneficiaryId; }
    public AidType getType() { return type; }
    public LocalDate getAidDate() { return aidDate; }
    public String getDescription() { return description; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getSharePerMember() { return sharePerMember; }
    public AidStatus getStatus() { return status; }
    public Long getSessionId() { return sessionId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    /** L'aide est-elle encore d'actualité ? */
    public boolean isCurrent() { return status.isCurrent(); }

    /**
     * Marque l'aide comme entièrement recouverte : tous les membres ont
     * versé leur part, elle n'est plus d'actualité.
     */
    public void complete() {
        this.status = AidStatus.completed;
    }
}
