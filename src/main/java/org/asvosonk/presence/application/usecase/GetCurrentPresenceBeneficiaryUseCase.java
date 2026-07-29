package org.asvosonk.presence.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.presence.domain.model.PresenceTourParticipant;
import org.asvosonk.presence.domain.repository.PresenceTourParticipantRepository;
import org.asvosonk.presence.domain.repository.PresenceTourRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * F-09 — Pure read of the next presence beneficiary.
 *
 * <p>This used to be a read-write {@code @Transactional} use case that
 * auto-closed the tour as a side effect when everyone had benefited. Because it
 * was invoked from the session-detail GET (a plain page view), simply opening
 * the page could silently close a tour. The close is now owned exclusively by
 * the explicit write flow {@link MarkPresenceBenefitedUseCase}, which closes the
 * tour when the last participant is marked. This use case only reads.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetCurrentPresenceBeneficiaryUseCase {

    private final PresenceTourRepository tourRepository;
    private final PresenceTourParticipantRepository participantRepository;

    /**
     * Returns the next participant who should be the beneficiary of the current
     * open tour, or {@code null} if there is no open tour or everyone has already
     * benefited. Never mutates state.
     */
    public PresenceTourParticipant peekNextBeneficiary() {
        var openTour = tourRepository.findCurrentOpenTour();
        if (openTour.isEmpty()) {
            return null;
        }
        return participantRepository.findNextBeneficiary(openTour.get().getId())
            .orElse(null);
    }
}
