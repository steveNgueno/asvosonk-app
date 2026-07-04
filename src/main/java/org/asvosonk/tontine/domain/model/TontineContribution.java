package org.asvosonk.tontine.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.asvosonk.tontine.domain.valueobject.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@EqualsAndHashCode(of = "id")
public class TontineContribution {

    private final Long id;
    private final Long tourId;
    private final Long sessionId;
    private final Long contributorId;
    private final Long beneficiaryId;
    private final BigDecimal amount;
    private final PaymentStatus status;
    private final LocalDateTime createdAt;

    public TontineContribution(Long id, Long tourId, Long sessionId,
                               Long contributorId, Long beneficiaryId,
                               BigDecimal amount, PaymentStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.tourId = tourId;
        this.sessionId = sessionId;
        this.contributorId = contributorId;
        this.beneficiaryId = beneficiaryId;
        this.amount = amount;
        this.status = status;
        this.createdAt = createdAt;
    }

    public boolean isPaid() {
        return status == PaymentStatus.paid;
    }

    public boolean isDefault() {
        return status == PaymentStatus.default_status;
    }
}
