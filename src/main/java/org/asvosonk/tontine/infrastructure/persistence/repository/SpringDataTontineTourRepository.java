package org.asvosonk.tontine.infrastructure.persistence.repository;

import org.asvosonk.tontine.domain.valueobject.TontineTourStatus;
import org.asvosonk.tontine.infrastructure.persistence.entity.TontineTourEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataTontineTourRepository extends JpaRepository<TontineTourEntity, Long> {
    List<TontineTourEntity> findAllByOrderByStartDateDesc();
    List<TontineTourEntity> findByStatusOrderByStartDateDesc(TontineTourStatus status);
    Optional<TontineTourEntity> findTopByStatusOrderByStartDateDesc(TontineTourStatus status);
}
