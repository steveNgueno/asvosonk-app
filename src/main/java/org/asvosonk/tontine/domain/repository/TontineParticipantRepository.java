package org.asvosonk.tontine.domain.repository;

import org.asvosonk.tontine.domain.model.TontineParticipant;

import java.util.List;
import java.util.Optional;

public interface TontineParticipantRepository {

    TontineParticipant save(TontineParticipant participant);

    List<TontineParticipant> findByTourId(Long tourId);

    List<TontineParticipant> findByTourIdOrderByDrawOrder(Long tourId);

    Optional<TontineParticipant> findByTourIdAndMemberId(Long tourId, Long memberId);
}
