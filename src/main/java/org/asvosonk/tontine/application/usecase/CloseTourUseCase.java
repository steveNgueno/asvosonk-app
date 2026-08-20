package org.asvosonk.tontine.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.common.domain.exception.ResourceNotFoundException;
import org.asvosonk.tontine.domain.model.TontineParticipant;
import org.asvosonk.tontine.domain.model.TontineTour;
import org.asvosonk.tontine.domain.repository.TontineDebtRepository;
import org.asvosonk.tontine.domain.repository.TontineParticipantRepository;
import org.asvosonk.tontine.domain.repository.TontineTourRepository;
import org.asvosonk.tontine.domain.valueobject.DebtStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CloseTourUseCase {

    private final TontineTourRepository tourRepository;
    private final TontineParticipantRepository participantRepository;
    private final TontineDebtRepository debtRepository;

    /**
     * Close a tontine tour. All participants must have benefited and no
     * inter-member debt may still be outstanding (F-23) — closing with open
     * debts would freeze "who owes whom" with no tour left to settle it in.
     */
    @Transactional
    public TontineTour execute(Long tourId) {
        TontineTour tour = tourRepository.findById(tourId)
            .orElseThrow(() -> new ResourceNotFoundException("Tour de grande tontine", tourId));

        if (tour.isClosed()) {
            throw new BusinessRuleException("Ce tour est déjà clôturé.");
        }

        List<TontineParticipant> participants = participantRepository.findByTourId(tourId);
        boolean allBenefited = participants.stream().allMatch(TontineParticipant::isHasBenefited);

        if (!allBenefited) {
            throw new BusinessRuleException(
                "Tous les participants doivent avoir bénéficié avant de clôturer le tour.");
        }

        boolean hasOpenDebts = !debtRepository.findByTourIdAndStatus(tourId, DebtStatus.owed).isEmpty();
        if (hasOpenDebts) {
            throw new BusinessRuleException(
                "Des dettes entre participants sont encore ouvertes ; elles doivent être soldées avant de clôturer le tour.");
        }

        tour.close(LocalDate.now());
        return tourRepository.save(tour);
    }
}
