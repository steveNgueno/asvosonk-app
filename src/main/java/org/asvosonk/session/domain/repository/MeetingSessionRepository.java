package org.asvosonk.session.domain.repository;

import org.asvosonk.session.domain.model.MeetingSession;
import org.asvosonk.session.domain.valueobject.SessionStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MeetingSessionRepository {

    Optional<MeetingSession> findById(Long id);

    List<MeetingSession> findAllByOrderBySessionDateDesc();

    boolean existsBySessionDate(LocalDate date);

    Optional<MeetingSession> findByStatus(SessionStatus status);

    List<MeetingSession> findByStatusOrderBySessionDateDesc(SessionStatus status);

    MeetingSession save(MeetingSession session);
}
