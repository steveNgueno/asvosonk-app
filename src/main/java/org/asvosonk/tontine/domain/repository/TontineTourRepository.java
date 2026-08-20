package org.asvosonk.tontine.domain.repository;

import org.asvosonk.tontine.domain.model.TontineTour;
import org.asvosonk.tontine.domain.valueobject.TontineTourStatus;

import java.util.List;
import java.util.Optional;

public interface TontineTourRepository {

    TontineTour save(TontineTour tour);

    Optional<TontineTour> findById(Long id);

    /**
     * Loads the tour under a PESSIMISTIC_WRITE lock (F-34). Serializes
     * concurrent contribution submissions for the same tour so two defaults
     * processed at once cannot both compute the same "next" draw order and
     * collide on the (tour_id, draw_order) unique index.
     */
    Optional<TontineTour> findByIdForUpdate(Long id);

    List<TontineTour> findAllByOrderByStartDateDesc();

    List<TontineTour> findByStatusOrderByStartDateDesc(TontineTourStatus status);

    Optional<TontineTour> findCurrentOpenTour();
}
