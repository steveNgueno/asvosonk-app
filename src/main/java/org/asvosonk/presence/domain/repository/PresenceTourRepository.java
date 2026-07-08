package org.asvosonk.presence.domain.repository;

import org.asvosonk.presence.domain.model.PresenceTour;
import org.asvosonk.presence.domain.valueobject.PresenceTourStatus;

import java.util.List;
import java.util.Optional;

public interface PresenceTourRepository {

    PresenceTour save(PresenceTour tour);

    Optional<PresenceTour> findById(Long id);

    List<PresenceTour> findAllByOrderByStartDateDesc();

    List<PresenceTour> findByStatusOrderByStartDateDesc(PresenceTourStatus status);

    Optional<PresenceTour> findCurrentOpenTour();
}
