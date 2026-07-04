package org.asvosonk.tontine.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.asvosonk.tontine.domain.model.TontineDebt;
import org.asvosonk.tontine.domain.repository.TontineDebtRepository;
import org.asvosonk.tontine.domain.valueobject.DebtStatus;
import org.asvosonk.tontine.infrastructure.persistence.entity.TontineDebtEntity;
import org.asvosonk.tontine.infrastructure.persistence.mapper.TontineDebtMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Primary
@Repository
@RequiredArgsConstructor
public class JpaTontineDebtRepository implements TontineDebtRepository {

    private final SpringDataTontineDebtRepository springData;
    private static final TontineDebtMapper MAPPER = TontineDebtMapper.INSTANCE;

    @Override
    public TontineDebt save(TontineDebt debt) {
        TontineDebtEntity entity = MAPPER.toEntity(debt);
        if (debt.getId() != null) {
            entity.setId(debt.getId());
        }
        TontineDebtEntity saved = springData.save(entity);
        return MAPPER.toDomain(saved);
    }

    @Override
    public Optional<TontineDebt> findById(Long id) {
        return springData.findById(id).map(MAPPER::toDomain);
    }

    @Override
    public List<TontineDebt> findByTourIdOrderByCreatedAtDesc(Long tourId) {
        return springData.findByTourIdOrderByCreatedAtDesc(tourId).stream()
            .map(MAPPER::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<TontineDebt> findByTourIdAndStatus(Long tourId, DebtStatus status) {
        return springData.findByTourIdAndStatus(tourId, status).stream()
            .map(MAPPER::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<TontineDebt> findByTourIdAndDebtorIdAndCreditorIdAndStatus(
            Long tourId, Long debtorId, Long creditorId, DebtStatus status) {
        return springData.findByTourIdAndDebtorIdAndCreditorIdAndStatus(tourId, debtorId, creditorId, status)
            .map(MAPPER::toDomain);
    }

    @Override
    public List<TontineDebt> findByTourIdAndDebtorId(Long tourId, Long debtorId) {
        return springData.findByTourIdAndDebtorId(tourId, debtorId).stream()
            .map(MAPPER::toDomain)
            .collect(Collectors.toList());
    }

    @org.springframework.stereotype.Repository
    interface SpringDataTontineDebtRepository extends JpaRepository<TontineDebtEntity, Long> {
        List<TontineDebtEntity> findByTourIdOrderByCreatedAtDesc(Long tourId);
        List<TontineDebtEntity> findByTourIdAndStatus(Long tourId, DebtStatus status);
        Optional<TontineDebtEntity> findByTourIdAndDebtorIdAndCreditorIdAndStatus(
            Long tourId, Long debtorId, Long creditorId, DebtStatus status);
        List<TontineDebtEntity> findByTourIdAndDebtorId(Long tourId, Long debtorId);
    }
}
