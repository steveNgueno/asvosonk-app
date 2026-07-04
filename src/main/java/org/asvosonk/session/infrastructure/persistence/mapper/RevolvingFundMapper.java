package org.asvosonk.session.infrastructure.persistence.mapper;

import org.asvosonk.session.domain.model.RevolvingFund;
import org.asvosonk.session.infrastructure.persistence.entity.RevolvingFundEntity;

/**
 * Mapper between RevolvingFund domain model and RevolvingFundEntity JPA entity.
 */
public class RevolvingFundMapper {

    public static RevolvingFund toDomain(RevolvingFundEntity entity) {
        if (entity == null) return null;
        return new RevolvingFund(
            entity.getId(),
            entity.getMember().getId(),
            entity.getBalance(),
            entity.getUpdatedAt()
        );
    }

    public static RevolvingFundEntity toEntity(RevolvingFund domain) {
        if (domain == null) return null;
        RevolvingFundEntity entity = new RevolvingFundEntity();
        entity.setId(domain.getId());
        entity.setBalance(domain.getBalance());
        return entity;
    }
}
