package org.asvosonk.tontine.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.tontine.domain.model.TontineContribution;
import org.asvosonk.tontine.domain.model.TontineDebt;
import org.asvosonk.tontine.domain.model.TontineParticipant;
import org.asvosonk.tontine.domain.model.TontineTour;
import org.asvosonk.tontine.domain.repository.TontineContributionRepository;
import org.asvosonk.tontine.domain.repository.TontineParticipantRepository;
import org.asvosonk.tontine.domain.repository.TontineTourRepository;
import org.asvosonk.tontine.domain.valueobject.DebtStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Use case: centralizes all read queries for tontine tours.
 * Prevents controllers from accessing repositories directly (hexagonal architecture).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetTourSummaryUseCase {

    private final TontineTourRepository           tourRepository;
    private final TontineParticipantRepository    participantRepository;
    private final TontineContributionRepository   contributionRepository;
    private final GetTourDebtsUseCase             getTourDebtsUseCase;

    public List<TontineTour> findAllTours() {
        return tourRepository.findAllByOrderByStartDateDesc();
    }

    public TontineTour findTourById(Long id) {
        return tourRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Tour introuvable : " + id));
    }

    public TontineTour findCurrentOpenTour() {
        return tourRepository.findCurrentOpenTour().orElse(null);
    }

    public List<TontineParticipant> findParticipantsByTourId(Long tourId) {
        return participantRepository.findByTourIdOrderByDrawOrder(tourId);
    }

    public List<TontineContribution> findContributionsByTourId(Long tourId) {
        return contributionRepository.findByTourIdOrderByCreatedAtDesc(tourId);
    }

    public BigDecimal calculateTotalCollected(Long tourId) {
        return contributionRepository.findByTourIdOrderByCreatedAtDesc(tourId).stream()
            .filter(TontineContribution::isPaid)
            .map(TontineContribution::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<TontineDebt> findOwedDebtsByTourId(Long tourId) {
        return getTourDebtsUseCase.execute(tourId).stream()
            .filter(d -> d.getStatus() == DebtStatus.owed)
            .collect(Collectors.toList());
    }

    public boolean allParticipantsBenefited(Long tourId) {
        return participantRepository.findByTourIdOrderByDrawOrder(tourId).stream()
            .allMatch(TontineParticipant::isHasBenefited);
    }

    public List<TontineParticipant> findNotBenefitedYet(Long tourId) {
        return participantRepository.findByTourIdOrderByDrawOrder(tourId).stream()
            .filter(p -> !p.isHasBenefited())
            .collect(Collectors.toList());
    }
}
