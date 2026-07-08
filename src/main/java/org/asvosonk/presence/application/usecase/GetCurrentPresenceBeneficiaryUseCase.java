package org.asvosonk.presence.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.presence.domain.model.PresenceTourParticipant;
import org.asvosonk.presence.domain.repository.PresenceTourParticipantRepository;
import org.asvosonk.presence.domain.repository.PresenceTourRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class GetCurrentPresenceBeneficiaryUseCase {

    private final PresenceTourRepository tourRepository;
    private final PresenceTourParticipantRepository participantRepository;
    private final ClosePresenceTourUseCase closePresenceTourUseCase;

    /**
     * Returns the next participant who should be the beneficiary.
     * If all participants have benefited, auto-closes the tour and returns null.
     */
    public PresenceTourParticipant execute() {
        var openTour = tourRepository.findCurrentOpenTour();
        if (openTour.isEmpty()) {
            return null;
        }

        Long tourId = openTour.get().getId();
        var next = participantRepository.findNextBeneficiary(tourId);

        if (next.isPresent()) {
            return next.get();
        }

        // All have benefited → auto-close the tour
        closePresenceTourUseCase.execute(tourId);
        return null;
    }
}
