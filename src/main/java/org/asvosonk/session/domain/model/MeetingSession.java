package org.asvosonk.session.domain.model;

import org.asvosonk.session.domain.valueobject.SessionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Pure domain model for a meeting session.
 * All JPA concerns are handled by MeetingSessionEntity in the infrastructure layer.
 */
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

    public MeetingSession(Long id, LocalDate sessionDate, SessionStatus status, String agenda,
                          Long beneficiaryMemberId, LocalDateTime closedAt, Long createdByUserId,
                          LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.sessionDate = sessionDate;
        this.status = status;
        this.agenda = agenda;
        this.beneficiaryMemberId = beneficiaryMemberId;
        this.closedAt = closedAt;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public LocalDate getSessionDate() { return sessionDate; }
    public SessionStatus getStatus() { return status; }
    public String getAgenda() { return agenda; }
    public Long getBeneficiaryMemberId() { return beneficiaryMemberId; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public boolean isClosed()  { return status == SessionStatus.closed; }
    public boolean isOpen()    { return status == SessionStatus.open; }
    public boolean isPlanned() { return status == SessionStatus.planned; }
}
