package org.asvosonk.sanction.infrastructure.persistence.mapper;

import org.asvosonk.sanction.domain.model.Sanction;
import org.asvosonk.sanction.infrastructure.persistence.entity.SanctionEntity;

/**
 * Mapper between Sanction domain model and SanctionEntity JPA entity.
 */
public class SanctionMapper {

    public static Sanction toDomain(SanctionEntity entity) {
        if (entity == null) return null;
        return new Sanction(
            entity.getId(),
            entity.getMember().getId(),
            entity.getSanctionDate(),
            entity.getAmount(),
            entity.getReason(),
            entity.getOrigin(),
            entity.getReferenceId(),
            entity.getStatus(),
            entity.getPaymentDate(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    public static SanctionEntity toEntity(Sanction domain) {
        if (domain == null) return null;
        SanctionEntity entity = new SanctionEntity();
        entity.setId(domain.getId());
        entity.setSanctionDate(domain.getSanctionDate());
        entity.setAmount(domain.getAmount());
        entity.setReason(domain.getReason());
        entity.setOrigin(domain.getOrigin());
        entity.setReferenceId(domain.getReferenceId());
        entity.setStatus(domain.getStatus());
        entity.setPaymentDate(domain.getPaymentDate());
        return entity;
    }
}
