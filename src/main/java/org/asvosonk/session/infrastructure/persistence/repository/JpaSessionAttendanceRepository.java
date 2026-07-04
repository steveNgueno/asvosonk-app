package org.asvosonk.session.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.asvosonk.session.domain.model.SessionAttendance;
import org.asvosonk.session.domain.repository.SessionAttendanceRepository;
import org.asvosonk.session.infrastructure.persistence.entity.SessionAttendanceEntity;
import org.asvosonk.session.infrastructure.persistence.mapper.SessionAttendanceMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Primary
@Repository
@RequiredArgsConstructor
public class JpaSessionAttendanceRepository implements SessionAttendanceRepository {

    private final SpringDataSessionAttendanceRepository springData;

    @Override
    public List<SessionAttendance> findBySessionIdOrderByMemberFullNameAsc(Long sessionId) {
        return springData.findBySessionIdOrderByMemberFullNameAsc(sessionId).stream()
            .map(SessionAttendanceMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<SessionAttendance> findBySessionIdAndMemberId(Long sessionId, Long memberId) {
        return springData.findBySessionIdAndMemberId(sessionId, memberId)
            .map(SessionAttendanceMapper::toDomain);
    }

    @Override
    public SessionAttendance save(SessionAttendance attendance) {
        // Full entity mapping handled at the service level due to complex relationships
        SessionAttendanceEntity entity = SessionAttendanceMapper.toEntity(attendance);
        return SessionAttendanceMapper.toDomain(springData.save(entity));
    }

    @org.springframework.stereotype.Repository
    interface SpringDataSessionAttendanceRepository extends JpaRepository<SessionAttendanceEntity, Long> {
        List<SessionAttendanceEntity> findBySessionIdOrderByMemberFullNameAsc(Long sessionId);
        Optional<SessionAttendanceEntity> findBySessionIdAndMemberId(Long sessionId, Long memberId);
    }
}
