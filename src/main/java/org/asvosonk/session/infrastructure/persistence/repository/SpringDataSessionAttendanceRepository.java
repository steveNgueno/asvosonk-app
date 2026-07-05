package org.asvosonk.session.infrastructure.persistence.repository;

import org.asvosonk.session.infrastructure.persistence.entity.SessionAttendanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataSessionAttendanceRepository extends JpaRepository<SessionAttendanceEntity, Long> {
    List<SessionAttendanceEntity> findBySessionIdOrderByMemberFullNameAsc(Long sessionId);
    Optional<SessionAttendanceEntity> findBySessionIdAndMemberId(Long sessionId, Long memberId);
}
