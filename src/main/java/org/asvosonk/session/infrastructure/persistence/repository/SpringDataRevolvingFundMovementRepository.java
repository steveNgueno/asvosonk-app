package org.asvosonk.session.infrastructure.persistence.repository;

import org.asvosonk.session.infrastructure.persistence.entity.RevolvingFundMovementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataRevolvingFundMovementRepository extends JpaRepository<RevolvingFundMovementEntity, Long> {
    List<RevolvingFundMovementEntity> findBySessionId(Long sessionId);
    List<RevolvingFundMovementEntity> findByFundIdOrderByCreatedAtAsc(Long fundId);
    @Query("""
            SELECT m FROM RevolvingFundMovementEntity m
            WHERE m.fund.member.id = :memberId
              AND m.movementType = 'advance'
              AND m.recovered = false
            ORDER BY m.session.sessionDate ASC
            """)
    List<RevolvingFundMovementEntity> findPendingAdvances(Long memberId);

    List<RevolvingFundMovementEntity> findByFundMemberIdOrderByCreatedAtDesc(Long memberId);
}
