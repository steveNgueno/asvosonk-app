package org.asvosonk.presence.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.common.domain.exception.ResourceNotFoundException;
import org.asvosonk.presence.domain.model.PresenceTourParticipant;
import org.asvosonk.presence.domain.repository.PresenceTourParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarkPresenceBenefitedUseCase {

    private final PresenceTourParticipantRepository participantRepository;
    private final ClosePresenceTourUseCase closePresenceTourUseCase;

    /**
     * Mark a participant as having benefited and optionally close the tour
     * if all participants have now benefited.
     *
     * @param tourId    the tour ID
     * @param memberId  the member who benefited
     * @param sessionId the session where they benefited
     * @return the updated PresenceTourParticipant
     */
    @Transactional
    public PresenceTourParticipant execute(Long tourId, Long memberId, Long sessionId) {
        PresenceTourParticipant participant = participantRepository
            .findByTourIdAndMemberId(tourId, memberId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Participant introuvable pour le tour " + tourId));

        participant.markAsBenefited(sessionId);
        PresenceTourParticipant saved = participantRepository.save(participant);

        // If all participants have benefited → close the tour
        long total = participantRepository.findByTourId(tourId).size();
        long benefited = participantRepository.countByTourIdAndHasBenefited(tourId, true);
        if (total == benefited) {
            closePresenceTourUseCase.execute(tourId);
        }

        return saved;
    }
}
