package org.asvosonk.presence.infrastructure.persistence.repository;

import org.asvosonk.presence.infrastructure.persistence.entity.PresenceTourParticipantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataPresenceTourParticipantRepository extends JpaRepository<PresenceTourParticipantEntity, Long> {

    List<PresenceTourParticipantEntity> findByTourIdOrderByDrawOrder(Long tourId);

    Optional<PresenceTourParticipantEntity> findByTourIdAndMemberId(Long tourId, Long memberId);

    @Query("SELECT p FROM PresenceTourParticipantEntity p " +
           "WHERE p.tourId = :tourId AND p.hasBenefited = false " +
           "ORDER BY p.drawOrder ASC LIMIT 1")
    Optional<PresenceTourParticipantEntity> findNextNotBenefited(@Param("tourId") Long tourId);

    long countByTourIdAndHasBenefited(Long tourId, boolean hasBenefited);
}
