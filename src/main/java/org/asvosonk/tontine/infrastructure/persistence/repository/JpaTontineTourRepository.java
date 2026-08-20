package org.asvosonk.tontine.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.asvosonk.tontine.domain.model.TontineTour;
import org.asvosonk.tontine.domain.repository.TontineTourRepository;
import org.asvosonk.tontine.domain.valueobject.TontineTourStatus;
import org.asvosonk.tontine.infrastructure.persistence.entity.TontineTourEntity;
import org.asvosonk.tontine.infrastructure.persistence.mapper.TontineTourMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Primary
@Repository
@RequiredArgsConstructor
public class JpaTontineTourRepository implements TontineTourRepository {

    private final SpringDataTontineTourRepository springData;
    private static final TontineTourMapper MAPPER = TontineTourMapper.INSTANCE;

    @Override
    public TontineTour save(TontineTour tour) {
        TontineTourEntity entity = MAPPER.toEntity(tour);
        if (tour.getId() != null) {
            entity.setId(tour.getId());
        }
        TontineTourEntity saved = springData.save(entity);
        return MAPPER.toDomain(saved);
    }

    @Override
    public Optional<TontineTour> findById(Long id) {
        return springData.findById(id).map(MAPPER::toDomain);
    }

    @Override
    public Optional<TontineTour> findByIdForUpdate(Long id) {
        return springData.findByIdForUpdate(id).map(MAPPER::toDomain);
    }

    @Override
    public List<TontineTour> findAllByOrderByStartDateDesc() {
        return springData.findAllByOrderByStartDateDesc().stream()
            .map(MAPPER::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<TontineTour> findByStatusOrderByStartDateDesc(TontineTourStatus status) {
        return springData.findByStatusOrderByStartDateDesc(status).stream()
            .map(MAPPER::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<TontineTour> findCurrentOpenTour() {
        return springData.findTopByStatusOrderByStartDateDesc(TontineTourStatus.open)
            .map(MAPPER::toDomain);
    }
}
