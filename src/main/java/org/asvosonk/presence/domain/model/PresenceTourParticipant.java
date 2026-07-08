package org.asvosonk.presence.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@EqualsAndHashCode(of = "id")
public class PresenceTourParticipant {

    private final Long id;
    private final Long tourId;
    private final Long memberId;
    private int drawOrder;
    private boolean hasBenefited;
    private Long sessionId;
    private final LocalDateTime createdAt;

    public PresenceTourParticipant(Long id, Long tourId, Long memberId,
                                   int drawOrder, boolean hasBenefited,
                                   Long sessionId, LocalDateTime createdAt) {
        this.id = id;
        this.tourId = tourId;
        this.memberId = memberId;
        this.drawOrder = drawOrder;
        this.hasBenefited = hasBenefited;
        this.sessionId = sessionId;
        this.createdAt = createdAt;
    }

    // ── Business methods ────────────────────────────────────

    public void markAsBenefited(Long sessionId) {
        this.hasBenefited = true;
        this.sessionId = sessionId;
    }
}
