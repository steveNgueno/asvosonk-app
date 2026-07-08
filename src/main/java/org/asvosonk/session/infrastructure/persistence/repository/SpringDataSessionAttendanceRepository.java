package org.asvosonk.session.infrastructure.persistence.repository;

import org.asvosonk.session.infrastructure.persistence.entity.SessionAttendanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataSessionAttendanceRepository extends JpaRepository<SessionAttendanceEntity, Long> {
    @Query("""
    SELECT sa
    FROM SessionAttendanceEntity sa
    JOIN FETCH sa.member m
    WHERE sa.session.id = :sessionId
    ORDER BY m.fullName
""")
    List<SessionAttendanceEntity> findBySessionIdOrderByMemberFullNameAsc(@Param("sessionId") Long sessionId);
    Optional<SessionAttendanceEntity> findBySessionIdAndMemberId(Long sessionId, Long memberId);
}
