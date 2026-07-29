package org.asvosonk.presence.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.common.domain.exception.ResourceNotFoundException;
import org.asvosonk.presence.domain.model.PresenceTour;
import org.asvosonk.presence.domain.model.PresenceTourParticipant;
import org.asvosonk.presence.domain.repository.PresenceTourParticipantRepository;
import org.asvosonk.presence.domain.repository.PresenceTourRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarkPresenceBenefitedUseCase {

    private final PresenceTourParticipantRepository participantRepository;
    private final PresenceTourRepository tourRepository;
    private final ClosePresenceTourUseCase closePresenceTourUseCase;

    /**
     * Mark a participant as having benefited, then close the tour if everyone
     * has now benefited.
     *
     * <p>F-08 — These invariants used to live only in the UI (the button was
     * hidden), so a forged/replayed POST could serve someone twice or out of
     * order. They are now enforced server-side:
     * <ul>
     *   <li>the tour must still be open;</li>
     *   <li>the participant must not already have benefited (no double-serving);</li>
     *   <li>the participant must be the next one in draw order (rotation equity).</li>
     * </ul>
     *
     * @param tourId    the tour ID
     * @param memberId  the member who benefited
     * @param sessionId the session where they benefited
     * @return the updated PresenceTourParticipant
     */
    @Transactional
    public PresenceTourParticipant execute(Long tourId, Long memberId, Long sessionId) {
        PresenceTour tour = tourRepository.findById(tourId)
            .orElseThrow(() -> new ResourceNotFoundException("Tour de présence", tourId));
        if (tour.isClosed()) {
            throw new BusinessRuleException("Ce tour de présence est clôturé.");
        }

        PresenceTourParticipant participant = participantRepository
            .findByTourIdAndMemberId(tourId, memberId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Participant introuvable pour le tour " + tourId));

        if (participant.isHasBenefited()) {
            throw new BusinessRuleException(
                "Ce membre a déjà bénéficié dans ce tour de présence.");
        }

        // Passage order: only the next non-benefited participant (smallest draw
        // order) may be served, so the rotation stays fair.
        PresenceTourParticipant next = participantRepository.findNextBeneficiary(tourId)
            .orElseThrow(() -> new BusinessRuleException(
                "Aucun bénéficiaire en attente dans ce tour."));
        if (!next.getMemberId().equals(memberId)) {
            throw new BusinessRuleException(
                "Ce membre n'est pas le prochain bénéficiaire dans l'ordre de passage.");
        }

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
