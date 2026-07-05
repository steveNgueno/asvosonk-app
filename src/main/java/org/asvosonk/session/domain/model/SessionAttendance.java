package org.asvosonk.session.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.asvosonk.session.domain.valueobject.AttendanceStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pure domain model for session attendance.
 * All JPA concerns are handled by SessionAttendanceEntity in the infrastructure layer.
 */
@Getter
@AllArgsConstructor
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

}
