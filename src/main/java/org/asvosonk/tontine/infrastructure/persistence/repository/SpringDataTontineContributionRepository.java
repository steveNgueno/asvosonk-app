package org.asvosonk.tontine.infrastructure.persistence.repository;

import org.asvosonk.tontine.infrastructure.persistence.entity.TontineContributionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataTontineContributionRepository extends JpaRepository<TontineContributionEntity, Long> {
    List<TontineContributionEntity> findByTourIdOrderByCreatedAtDesc(Long tourId);
    List<TontineContributionEntity> findByTourIdAndSessionId(Long tourId, Long sessionId);
    List<TontineContributionEntity> findByContributorIdAndTourId(Long contributorId, Long tourId);
}
