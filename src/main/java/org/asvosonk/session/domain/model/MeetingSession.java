package org.asvosonk.session.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.asvosonk.session.domain.valueobject.SessionStatus;
import org.asvosonk.session.domain.valueobject.SessionStep;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Pure domain model for a meeting session.
 * All JPA concerns are handled by MeetingSessionEntity in the infrastructure layer.
 */
@Getter
@AllArgsConstructor
public class MeetingSession {

    private final Long id;
    private LocalDate sessionDate;
    private SessionStatus status;
    private String agenda;
    private Long beneficiaryMemberId;
    private LocalDateTime closedAt;
    private Long createdByUserId;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private SessionStep currentStep;

    public boolean isClosed()  { return status == SessionStatus.closed; }
    public boolean isOpen()    { return status == SessionStatus.open; }
    public boolean isPlanned() { return status == SessionStatus.planned; }
}
