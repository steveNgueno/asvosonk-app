package org.asvosonk.presence.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.asvosonk.presence.domain.model.PresenceTour;
import org.asvosonk.presence.domain.repository.PresenceTourRepository;
import org.asvosonk.presence.domain.valueobject.PresenceTourStatus;
import org.asvosonk.presence.infrastructure.persistence.entity.PresenceTourEntity;
import org.asvosonk.presence.infrastructure.persistence.mapper.PresenceTourMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Primary
@Repository
@RequiredArgsConstructor
public class JpaPresenceTourRepository implements PresenceTourRepository {

    private final SpringDataPresenceTourRepository springData;
    private static final PresenceTourMapper MAPPER = PresenceTourMapper.INSTANCE;

    @Override
    public PresenceTour save(PresenceTour tour) {
        PresenceTourEntity entity = MAPPER.toEntity(tour);
        if (tour.getId() != null) {
            entity.setId(tour.getId());
        }
        PresenceTourEntity saved = springData.save(entity);
        return MAPPER.toDomain(saved);
    }

    @Override
    public Optional<PresenceTour> findById(Long id) {
        return springData.findById(id).map(MAPPER::toDomain);
    }

    @Override
    public List<PresenceTour> findAllByOrderByStartDateDesc() {
        return springData.findAllByOrderByStartDateDesc().stream()
            .map(MAPPER::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<PresenceTour> findByStatusOrderByStartDateDesc(PresenceTourStatus status) {
        return springData.findByStatusOrderByStartDateDesc(status).stream()
            .map(MAPPER::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<PresenceTour> findCurrentOpenTour() {
        return springData.findTopByStatusOrderByStartDateDesc(PresenceTourStatus.open)
            .map(MAPPER::toDomain);
    }
}
