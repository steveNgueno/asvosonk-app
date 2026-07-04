package org.asvosonk.session.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.asvosonk.session.domain.model.RevolvingFundMovement;
import org.asvosonk.session.domain.repository.RevolvingFundMovementRepository;
import org.asvosonk.session.infrastructure.persistence.entity.RevolvingFundMovementEntity;
import org.asvosonk.session.infrastructure.persistence.mapper.RevolvingFundMovementMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Primary
@Repository
@RequiredArgsConstructor
public class JpaRevolvingFundMovementRepository implements RevolvingFundMovementRepository {

    private final SpringDataRevolvingFundMovementRepository springData;

    @Override
    public List<RevolvingFundMovement> findPendingAdvances(Long memberId) {
        return springData.findPendingAdvances(memberId).stream()
            .map(RevolvingFundMovementMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<RevolvingFundMovement> findByFundMemberIdOrderByCreatedAtDesc(Long memberId) {
        return springData.findByFundMemberIdOrderByCreatedAtDesc(memberId).stream()
            .map(RevolvingFundMovementMapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public RevolvingFundMovement save(RevolvingFundMovement movement) {
        RevolvingFundMovementEntity entity = RevolvingFundMovementMapper.toEntity(movement);
        return RevolvingFundMovementMapper.toDomain(springData.save(entity));
    }

    @org.springframework.stereotype.Repository
    interface SpringDataRevolvingFundMovementRepository extends JpaRepository<RevolvingFundMovementEntity, Long> {

        @Query("""
            SELECT m FROM RevolvingFundMovementEntity m
            WHERE m.fund.member.id = :memberId
              AND m.movementType = 'advance'
              AND m.recovered = false
            ORDER BY m.session.sessionDate ASC
            """)
        List<RevolvingFundMovementEntity> findPendingAdvances(Long memberId);

        List<RevolvingFundMovementEntity> findByFundMemberIdOrderByCreatedAtDesc(Long memberId);
    }
}
