package org.asvosonk.tontine.domain.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.asvosonk.tontine.domain.valueobject.TontineTourStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@EqualsAndHashCode(of = "id")
public class TontineTour {

    private final Long id;
    private final LocalDate startDate;
    private LocalDate endDate;
    private TontineTourStatus status;
    private final LocalDateTime createdAt;

    public TontineTour(Long id, LocalDate startDate, LocalDate endDate,
                       TontineTourStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.createdAt = createdAt;
    }

    // ── Business methods ────────────────────────────────────

    public boolean isOpen() {
        return status == TontineTourStatus.open;
    }

    public boolean isClosed() {
        return status == TontineTourStatus.closed;
    }

    public void close(LocalDate endDate) {
        this.status = TontineTourStatus.closed;
        this.endDate = endDate;
    }
}
