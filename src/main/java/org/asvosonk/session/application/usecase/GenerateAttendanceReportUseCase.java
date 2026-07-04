package org.asvosonk.session.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.session.domain.model.MeetingSession;
import org.asvosonk.session.domain.model.SessionAttendance;
import org.asvosonk.session.domain.repository.MeetingSessionRepository;
import org.asvosonk.session.domain.repository.SessionAttendanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Use case: generate an attendance report for a session.
 * Uses domain repository ports (clean architecture).
 */
@Service
@RequiredArgsConstructor
public class GenerateAttendanceReportUseCase {

    private final MeetingSessionRepository meetingSessionRepository;
    private final SessionAttendanceRepository sessionAttendanceRepository;

    /**
     * Returns the session domain model for the report.
     */
    @Transactional(readOnly = true)
    public MeetingSession getSession(Long sessionId) {
        return meetingSessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Séance introuvable : " + sessionId));
    }

    /**
     * Returns all attendance records for a session, ordered by member name.
     */
    @Transactional(readOnly = true)
    public List<SessionAttendance> getAttendances(Long sessionId) {
        return sessionAttendanceRepository.findBySessionIdOrderByMemberFullNameAsc(sessionId);
    }
}
