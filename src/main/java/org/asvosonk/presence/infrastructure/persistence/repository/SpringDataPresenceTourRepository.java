package org.asvosonk.presence.infrastructure.persistence.repository;

import org.asvosonk.presence.domain.valueobject.PresenceTourStatus;
import org.asvosonk.presence.infrastructure.persistence.entity.PresenceTourEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataPresenceTourRepository extends JpaRepository<PresenceTourEntity, Long> {

    List<PresenceTourEntity> findAllByOrderByStartDateDesc();

    List<PresenceTourEntity> findByStatusOrderByStartDateDesc(PresenceTourStatus status);

    Optional<PresenceTourEntity> findTopByStatusOrderByStartDateDesc(PresenceTourStatus status);
}
