package org.asvosonk.aid.infrastructure.persistence.mapper;

import org.asvosonk.aid.domain.model.AidContribution;
import org.asvosonk.aid.infrastructure.persistence.entity.AidContributionEntity;

public class AidContributionMapper {

    public static AidContribution toDomain(AidContributionEntity entity) {
        if (entity == null) return null;
        return new AidContribution(
            entity.getId(),
            entity.getAid().getId(),
            entity.getMember().getId(),
            entity.getAmountDue(),
            entity.getAmountPaid(),
            entity.getStatus(),
            entity.getPaymentMode(),
            entity.getPaymentDate(),
            entity.getSession() != null ? entity.getSession().getId() : null,
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    public static AidContributionEntity toEntity(AidContribution domain) {
        if (domain == null) return null;
        AidContributionEntity entity = new AidContributionEntity();
        entity.setId(domain.getId());
        entity.setAmountDue(domain.getAmountDue());
        entity.setAmountPaid(domain.getAmountPaid());
        entity.setStatus(domain.getStatus());
        entity.setPaymentMode(domain.getPaymentMode());
        entity.setPaymentDate(domain.getPaymentDate());
        return entity;
    }
}
