package org.asvosonk.tontine.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@EqualsAndHashCode(of = "id")
public class TontineParticipant {

    private final Long id;
    private final Long tourId;
    private final Long memberId;
    // F-34 — mutable by design: a member who defaults before benefiting is
    // pushed to last place (see RecordTontineContributionUseCase.handleDefault).
    // The V1 schema comment calling this column "immutable" predates that rule.
    private int drawOrder;
    private boolean hasBenefited;
    private final LocalDateTime createdAt;

    public TontineParticipant(Long id, Long tourId, Long memberId,
                              int drawOrder, boolean hasBenefited, LocalDateTime createdAt) {
        this.id = id;
        this.tourId = tourId;
        this.memberId = memberId;
        this.drawOrder = drawOrder;
        this.hasBenefited = hasBenefited;
        this.createdAt = createdAt;
    }

    // ── Business methods ────────────────────────────────────

    public void markAsBenefited() {
        this.hasBenefited = true;
    }

    public void reassignDrawOrder(int newOrder) {
        this.drawOrder = newOrder;
    }
}
