package org.asvosonk.session.infrastructure.persistence.repository;

import org.asvosonk.session.infrastructure.persistence.entity.SessionReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SessionReportRepository extends JpaRepository<SessionReportEntity, Long> {
    Optional<SessionReportEntity> findBySessionId(Long sessionId);
}
