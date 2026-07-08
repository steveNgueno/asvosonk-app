package org.asvosonk.presence.domain.repository;

import org.asvosonk.presence.domain.model.PresenceTourParticipant;

import java.util.List;
import java.util.Optional;

public interface PresenceTourParticipantRepository {

    PresenceTourParticipant save(PresenceTourParticipant participant);

    List<PresenceTourParticipant> findByTourId(Long tourId);

    List<PresenceTourParticipant> findByTourIdOrderByDrawOrder(Long tourId);

    Optional<PresenceTourParticipant> findByTourIdAndMemberId(Long tourId, Long memberId);

    /**
     * Find the participant with the smallest draw order who hasn't benefited yet.
     */
    Optional<PresenceTourParticipant> findNextBeneficiary(Long tourId);

    long countByTourIdAndHasBenefited(Long tourId, boolean hasBenefited);
}
