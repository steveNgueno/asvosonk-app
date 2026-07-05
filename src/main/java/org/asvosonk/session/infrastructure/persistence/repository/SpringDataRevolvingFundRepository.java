package org.asvosonk.session.infrastructure.persistence.repository;

import org.asvosonk.session.infrastructure.persistence.entity.RevolvingFundEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpringDataRevolvingFundRepository extends JpaRepository<RevolvingFundEntity, Long> {
    Optional<RevolvingFundEntity> findByMemberId(Long memberId);
}
