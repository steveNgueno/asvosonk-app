package org.asvosonk.tontine.infrastructure.persistence.repository;

import org.asvosonk.tontine.domain.valueobject.TontineTourStatus;
import org.asvosonk.tontine.infrastructure.persistence.entity.TontineTourEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataTontineTourRepository extends JpaRepository<TontineTourEntity, Long> {
    List<TontineTourEntity> findAllByOrderByStartDateDesc();
    List<TontineTourEntity> findByStatusOrderByStartDateDesc(TontineTourStatus status);
    Optional<TontineTourEntity> findTopByStatusOrderByStartDateDesc(TontineTourStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TontineTourEntity t where t.id = :id")
    Optional<TontineTourEntity> findByIdForUpdate(Long id);
}
