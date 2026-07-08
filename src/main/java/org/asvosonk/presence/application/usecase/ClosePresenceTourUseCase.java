package org.asvosonk.presence.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.common.domain.exception.ResourceNotFoundException;
import org.asvosonk.presence.domain.model.PresenceTour;
import org.asvosonk.presence.domain.repository.PresenceTourRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ClosePresenceTourUseCase {

    private final PresenceTourRepository tourRepository;

    /**
     * Close a presence tour. No precondition checks — called automatically
     * when all participants have benefited.
     */
    @Transactional
    public PresenceTour execute(Long tourId) {
        PresenceTour tour = tourRepository.findById(tourId)
            .orElseThrow(() -> new ResourceNotFoundException("Tour de présence", tourId));

        if (tour.isClosed()) {
            throw new BusinessRuleException("Ce tour de présence est déjà clôturé.");
        }

        tour.close(LocalDate.now());
        return tourRepository.save(tour);
    }
}
