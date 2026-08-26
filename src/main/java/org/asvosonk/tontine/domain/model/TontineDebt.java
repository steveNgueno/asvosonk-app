package org.asvosonk.tontine.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.asvosonk.tontine.domain.valueobject.DebtStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@EqualsAndHashCode(of = "id")
public class TontineDebt {

    private final Long id;
    private final Long tourId;
    private final Long debtorId;
    private final Long creditorId;
    private final BigDecimal amount;
    private final Long originSessionId;
    private DebtStatus status;
    private Long repaymentSessionId;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public TontineDebt(Long id, Long tourId, Long debtorId, Long creditorId,
                       BigDecimal amount, Long originSessionId, DebtStatus status,
                       Long repaymentSessionId, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.tourId = tourId;
        this.debtorId = debtorId;
        this.creditorId = creditorId;
        this.amount = amount;
        this.originSessionId = originSessionId;
        this.status = status;
        this.repaymentSessionId = repaymentSessionId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ── Business methods ────────────────────────────────────

    public boolean isOwed() {
        return status == DebtStatus.owed;
    }

    public boolean isRepaid() {
        return status == DebtStatus.repaid;
    }

    public void markAsRepaid(Long repaymentSessionId) {
        this.status = DebtStatus.repaid;
        this.repaymentSessionId = repaymentSessionId;
        this.updatedAt = LocalDateTime.now();
    }

    public void markAsUnrepaid() {
        this.status = DebtStatus.owed;
        this.repaymentSessionId = null;
        this.updatedAt = LocalDateTime.now();
    }
}
