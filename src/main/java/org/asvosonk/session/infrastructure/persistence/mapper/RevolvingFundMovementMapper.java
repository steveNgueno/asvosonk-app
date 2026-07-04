package org.asvosonk.session.infrastructure.persistence.mapper;

import org.asvosonk.session.domain.model.RevolvingFundMovement;
import org.asvosonk.session.infrastructure.persistence.entity.RevolvingFundMovementEntity;

/**
 * Mapper between RevolvingFundMovement domain model and RevolvingFundMovementEntity JPA entity.
 */
public class RevolvingFundMovementMapper {

    public static RevolvingFundMovement toDomain(RevolvingFundMovementEntity entity) {
        if (entity == null) return null;
        return new RevolvingFundMovement(
            entity.getId(),
            entity.getFund().getId(),
            entity.getSession().getId(),
            entity.getMovementType(),
            entity.getAmount(),
            entity.isRecovered(),
            entity.getCreatedAt()
        );
    }

    public static RevolvingFundMovementEntity toEntity(RevolvingFundMovement domain) {
        if (domain == null) return null;
        RevolvingFundMovementEntity entity = new RevolvingFundMovementEntity();
        entity.setId(domain.getId());
        entity.setMovementType(domain.getMovementType());
        entity.setAmount(domain.getAmount());
        entity.setRecovered(domain.isRecovered());
        return entity;
    }
}
