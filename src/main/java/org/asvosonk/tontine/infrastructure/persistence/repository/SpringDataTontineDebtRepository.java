package org.asvosonk.tontine.infrastructure.persistence.repository;

import org.asvosonk.tontine.domain.valueobject.DebtStatus;
import org.asvosonk.tontine.infrastructure.persistence.entity.TontineDebtEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataTontineDebtRepository extends JpaRepository<TontineDebtEntity, Long> {
    List<TontineDebtEntity> findByTourIdOrderByCreatedAtDesc(Long tourId);
    List<TontineDebtEntity> findByTourIdAndStatus(Long tourId, DebtStatus status);
    Optional<TontineDebtEntity> findByTourIdAndDebtorIdAndCreditorIdAndStatus(
        Long tourId, Long debtorId, Long creditorId, DebtStatus status);
    List<TontineDebtEntity> findByTourIdAndDebtorId(Long tourId, Long debtorId);
}
