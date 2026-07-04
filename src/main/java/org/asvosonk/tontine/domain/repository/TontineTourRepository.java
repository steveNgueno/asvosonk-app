package org.asvosonk.tontine.domain.repository;

import org.asvosonk.tontine.domain.model.TontineTour;
import org.asvosonk.tontine.domain.valueobject.TontineTourStatus;

import java.util.List;
import java.util.Optional;

public interface TontineTourRepository {

    TontineTour save(TontineTour tour);

    Optional<TontineTour> findById(Long id);

    List<TontineTour> findAllByOrderByStartDateDesc();

    List<TontineTour> findByStatusOrderByStartDateDesc(TontineTourStatus status);

    Optional<TontineTour> findCurrentOpenTour();
}
