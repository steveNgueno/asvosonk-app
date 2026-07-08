package org.asvosonk.presence.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.asvosonk.presence.domain.valueobject.PresenceTourStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@EqualsAndHashCode(of = "id")
public class PresenceTour {

    private final Long id;
    private final LocalDate startDate;
    private LocalDate endDate;
    private PresenceTourStatus status;
    private final LocalDateTime createdAt;

    public PresenceTour(Long id, LocalDate startDate, LocalDate endDate,
                        PresenceTourStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.createdAt = createdAt;
    }

    // ── Business methods ────────────────────────────────────

    public boolean isOpen() {
        return status == PresenceTourStatus.open;
    }

    public boolean isClosed() {
        return status == PresenceTourStatus.closed;
    }

    public void close(LocalDate endDate) {
        this.status = PresenceTourStatus.closed;
        this.endDate = endDate;
    }
}
