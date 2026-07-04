package org.asvosonk.sanction.infrastructure.persistence.repository;

import org.asvosonk.sanction.domain.valueobject.SanctionStatus;
import org.asvosonk.sanction.infrastructure.persistence.entity.SanctionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SanctionJpaRepository extends JpaRepository<SanctionEntity, Long> {
    List<SanctionEntity> findByMemberIdOrderBySanctionDateDesc(Long memberId);
    List<SanctionEntity> findByStatusOrderBySanctionDateDesc(SanctionStatus status);
    List<SanctionEntity> findByMemberIdAndStatusOrderBySanctionDateDesc(Long memberId, SanctionStatus status);
}
