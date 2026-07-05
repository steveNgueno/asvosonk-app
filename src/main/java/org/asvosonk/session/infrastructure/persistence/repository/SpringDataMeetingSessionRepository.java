package org.asvosonk.session.infrastructure.persistence.repository;

import org.asvosonk.session.domain.valueobject.SessionStatus;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataMeetingSessionRepository extends JpaRepository<MeetingSessionEntity, Long> {
    Optional<MeetingSessionEntity> findBySessionDate(LocalDate sessionDate);
    long countByStatus(SessionStatus status);
    List<MeetingSessionEntity> findAllByOrderBySessionDateDesc();
    boolean existsBySessionDate(LocalDate date);
    Optional<MeetingSessionEntity> findByStatus(SessionStatus status);
    List<MeetingSessionEntity> findByStatusOrderBySessionDateDesc(SessionStatus status);
}
