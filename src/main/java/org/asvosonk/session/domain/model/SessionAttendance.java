package org.asvosonk.session.domain.model;

import org.asvosonk.session.domain.valueobject.AttendanceStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pure domain model for session attendance.
 * All JPA concerns are handled by SessionAttendanceEntity in the infrastructure layer.
 */
public class SessionAttendance {

    private final Long id;
    private final Long sessionId;
    private final Long memberId;
    private boolean present;
    private BigDecimal amountPaid;
    private boolean coveredByFund;
    private AttendanceStatus attendanceStatus;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SessionAttendance(Long id, Long sessionId, Long memberId,
                             boolean present, BigDecimal amountPaid,
                             boolean coveredByFund, AttendanceStatus attendanceStatus,
                             LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.memberId = memberId;
        this.present = present;
        this.amountPaid = amountPaid;
        this.coveredByFund = coveredByFund;
        this.attendanceStatus = attendanceStatus;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public Long getSessionId() { return sessionId; }
    public Long getMemberId() { return memberId; }
    public boolean isPresent() { return present; }
    public BigDecimal getAmountPaid() { return amountPaid; }
    public boolean isCoveredByFund() { return coveredByFund; }
    public AttendanceStatus getAttendanceStatus() { return attendanceStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
