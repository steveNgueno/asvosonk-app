package org.asvosonk.tontine.domain.repository;

import org.asvosonk.tontine.domain.model.TontineDebt;
import org.asvosonk.tontine.domain.valueobject.DebtStatus;

import java.util.List;
import java.util.Optional;

public interface TontineDebtRepository {

    TontineDebt save(TontineDebt debt);

    Optional<TontineDebt> findById(Long id);

    List<TontineDebt> findByTourIdOrderByCreatedAtDesc(Long tourId);

    List<TontineDebt> findByTourIdAndStatus(Long tourId, DebtStatus status);

    Optional<TontineDebt> findByTourIdAndDebtorIdAndCreditorIdAndStatus(
        Long tourId, Long debtorId, Long creditorId, DebtStatus status);

    List<TontineDebt> findByTourIdAndDebtorId(Long tourId, Long debtorId);

    void deleteById(Long id);
}
