package org.asvosonk.tontine.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.tontine.domain.model.TontineDebt;
import org.asvosonk.tontine.domain.repository.TontineDebtRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetTourDebtsUseCase {

    private final TontineDebtRepository debtRepository;

    /**
     * Get all debts for a tour, optionally filtered by member (as debtor or creditor).
     */
    public List<TontineDebt> execute(Long tourId) {
        return debtRepository.findByTourIdOrderByCreatedAtDesc(tourId);
    }

    /**
     * Get debts where the given member is the debtor.
     */
    public List<TontineDebt> findByDebtor(Long tourId, Long memberId) {
        return debtRepository.findByTourIdAndDebtorId(tourId, memberId);
    }
}
