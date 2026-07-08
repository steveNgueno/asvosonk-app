package org.asvosonk.presence.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.member.application.usecase.SearchMemberUseCase;
import org.asvosonk.presence.domain.model.PresenceTour;
import org.asvosonk.presence.domain.model.PresenceTourParticipant;
import org.asvosonk.presence.domain.repository.PresenceTourParticipantRepository;
import org.asvosonk.presence.domain.repository.PresenceTourRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Centralizes all read queries for presence tours.
 * Prevents controllers from accessing repositories directly (hexagonal architecture).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetPresenceTourSummaryUseCase {

    private final PresenceTourRepository tourRepository;
    private final PresenceTourParticipantRepository participantRepository;
    private final SearchMemberUseCase searchMemberUseCase;

    public List<PresenceTour> findAllTours() {
        return tourRepository.findAllByOrderByStartDateDesc();
    }

    public PresenceTour findTourById(Long id) {
        return tourRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Tour de présence introuvable : " + id));
    }

    public PresenceTour findCurrentOpenTour() {
        return tourRepository.findCurrentOpenTour().orElse(null);
    }

    public List<PresenceTourParticipant> findParticipantsByTourId(Long tourId) {
        return participantRepository.findByTourIdOrderByDrawOrder(tourId);
    }

    public List<PresenceTourParticipant> findNotBenefitedYet(Long tourId) {
        return participantRepository.findByTourIdOrderByDrawOrder(tourId).stream()
            .filter(p -> !p.isHasBenefited())
            .collect(Collectors.toList());
    }

    public boolean allParticipantsBenefited(Long tourId) {
        return participantRepository.findByTourIdOrderByDrawOrder(tourId).stream()
            .allMatch(PresenceTourParticipant::isHasBenefited);
    }

    public long countBenefited(Long tourId) {
        return participantRepository.countByTourIdAndHasBenefited(tourId, true);
    }
}
