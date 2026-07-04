package org.asvosonk.tontine.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.common.domain.exception.ResourceNotFoundException;
import org.asvosonk.tontine.domain.model.TontineParticipant;
import org.asvosonk.tontine.domain.repository.TontineParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarkBenefitedUseCase {

    private final TontineParticipantRepository participantRepository;

    /**
     * Mark a participant as having benefited from the grand tontine for a given tour.
     */
    @Transactional
    public TontineParticipant execute(Long tourId, Long memberId) {
        TontineParticipant participant = participantRepository.findByTourIdAndMemberId(tourId, memberId)
            .orElseThrow(() -> new ResourceNotFoundException("Participant introuvable pour le tour " + tourId));

        participant.markAsBenefited();
        return participantRepository.save(participant);
    }
}
