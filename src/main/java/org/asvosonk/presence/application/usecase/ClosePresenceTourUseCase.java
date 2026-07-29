package org.asvosonk.presence.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.common.domain.exception.ResourceNotFoundException;
import org.asvosonk.presence.domain.model.PresenceTour;
import org.asvosonk.presence.domain.repository.PresenceTourParticipantRepository;
import org.asvosonk.presence.domain.repository.PresenceTourRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ClosePresenceTourUseCase {

    private final PresenceTourRepository tourRepository;
    private final PresenceTourParticipantRepository participantRepository;

    /**
     * Close a presence tour once every participant has benefited.
     *
     * <p>F-08 — the "all have benefited" rule used to live only in the UI (the
     * close button was hidden), so a forged POST could close a tour mid-rotation
     * and deprive the remaining members. It is now enforced here. The automatic
     * close triggered by {@link MarkPresenceBenefitedUseCase} runs only when the
     * count is already complete, so this guard never blocks the happy path.
     */
    @Transactional
    public PresenceTour execute(Long tourId) {
        PresenceTour tour = tourRepository.findById(tourId)
            .orElseThrow(() -> new ResourceNotFoundException("Tour de présence", tourId));

        if (tour.isClosed()) {
            throw new BusinessRuleException("Ce tour de présence est déjà clôturé.");
        }

        long total = participantRepository.findByTourId(tourId).size();
        long benefited = participantRepository.countByTourIdAndHasBenefited(tourId, true);
        if (total == 0 || benefited < total) {
            throw new BusinessRuleException(
                "Impossible de clôturer : tous les participants n'ont pas encore bénéficié ("
              + benefited + "/" + total + ").");
        }

        tour.close(LocalDate.now());
        return tourRepository.save(tour);
    }
}
