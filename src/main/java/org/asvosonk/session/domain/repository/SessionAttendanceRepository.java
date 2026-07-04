package org.asvosonk.session.domain.repository;

import org.asvosonk.session.domain.model.SessionAttendance;

import java.util.List;
import java.util.Optional;

public interface SessionAttendanceRepository {

    List<SessionAttendance> findBySessionIdOrderByMemberFullNameAsc(Long sessionId);

    Optional<SessionAttendance> findBySessionIdAndMemberId(Long sessionId, Long memberId);

    SessionAttendance save(SessionAttendance attendance);
}
