package org.asvosonk.aid.infrastructure.persistence.repository;

import org.asvosonk.aid.domain.valueobject.AidContributionStatus;
import org.asvosonk.aid.infrastructure.persistence.entity.AidContributionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataAidContributionRepository extends JpaRepository<AidContributionEntity, Long> {

    List<AidContributionEntity> findByAidIdOrderByMemberFullNameAsc(Long aidId);

    List<AidContributionEntity> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    Optional<AidContributionEntity> findByAidIdAndMemberId(Long aidId, Long memberId);

    List<AidContributionEntity> findByAidIdAndStatus(Long aidId, AidContributionStatus status);

    List<AidContributionEntity> findByMemberIdAndStatusOrderByCreatedAtAsc(Long memberId, AidContributionStatus status);

    @Query("""
           SELECT COALESCE(SUM(c.amountPaid), 0) FROM AidContributionEntity c
            WHERE c.session.id = :sessionId
           """)
    BigDecimal totalPaidBySessionId(@Param("sessionId") Long sessionId);
}
