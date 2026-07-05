package org.asvosonk.tontine.infrastructure.persistence.repository;

import org.asvosonk.tontine.infrastructure.persistence.entity.TontineParticipantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataTontineParticipantRepository extends JpaRepository<TontineParticipantEntity, Long> {
    List<TontineParticipantEntity> findByTourIdOrderByDrawOrder(Long tourId);
    Optional<TontineParticipantEntity> findByTourIdAndMemberId(Long tourId, Long memberId);
}
