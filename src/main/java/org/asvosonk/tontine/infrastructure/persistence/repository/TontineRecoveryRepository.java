package org.asvosonk.tontine.infrastructure.persistence.repository;

import org.asvosonk.tontine.infrastructure.persistence.entity.TontineRecoveryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TontineRecoveryRepository extends JpaRepository<TontineRecoveryEntity, Long> {

    Optional<TontineRecoveryEntity> findByFailedContributionId(Long failedContributionId);

    List<TontineRecoveryEntity> findByTourId(Long tourId);

    List<TontineRecoveryEntity> findBySessionIdOrderByIdAsc(Long sessionId);
}
