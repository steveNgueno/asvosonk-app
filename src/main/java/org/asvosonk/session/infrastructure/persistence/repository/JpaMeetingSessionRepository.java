package org.asvosonk.session.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.asvosonk.session.domain.model.MeetingSession;
import org.asvosonk.session.domain.repository.MeetingSessionRepository;
import org.asvosonk.session.domain.valueobject.SessionStatus;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.asvosonk.session.infrastructure.persistence.mapper.MeetingSessionMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Primary
@Repository
@RequiredArgsConstructor
public class JpaMeetingSessionRepository implements MeetingSessionRepository {

    private final SpringDataMeetingSessionRepository springData;

    @Override
    public Optional<MeetingSession> findById(Long id) {
        return springData.findById(id).map(MeetingSessionMapper::toDomain);
    }

    @Override
    public List<MeetingSession> findAllByOrderBySessionDateDesc() {
        return springData.findAllByOrderBySessionDateDesc().stream()
            .map(MeetingSessionMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public boolean existsBySessionDate(LocalDate date) {
        return springData.existsBySessionDate(date);
    }

    @Override
    public Optional<MeetingSession> findByStatus(SessionStatus status) {
        return springData.findByStatus(status).map(MeetingSessionMapper::toDomain);
    }

    @Override
    public List<MeetingSession> findByStatusOrderBySessionDateDesc(SessionStatus status) {
        return springData.findByStatusOrderBySessionDateDesc(status).stream()
            .map(MeetingSessionMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public MeetingSession save(MeetingSession session) {
        MeetingSessionEntity entity = MeetingSessionMapper.toEntity(session);
        // Set references that mapper doesn't handle
        return MeetingSessionMapper.toDomain(springData.save(entity));
    }

    @org.springframework.stereotype.Repository
    interface SpringDataMeetingSessionRepository extends JpaRepository<MeetingSessionEntity, Long> {
        List<MeetingSessionEntity> findAllByOrderBySessionDateDesc();
        boolean existsBySessionDate(LocalDate date);
        Optional<MeetingSessionEntity> findByStatus(SessionStatus status);
        List<MeetingSessionEntity> findByStatusOrderBySessionDateDesc(SessionStatus status);
    }
}
