package org.asvosonk.aid.infrastructure.persistence.mapper;

import org.asvosonk.aid.domain.model.Aid;
import org.asvosonk.aid.infrastructure.persistence.entity.AidEntity;

public class AidMapper {

    public static Aid toDomain(AidEntity entity) {
        if (entity == null) return null;
        return new Aid(
            entity.getId(),
            entity.getBeneficiary().getId(),
            entity.getType(),
            entity.getAidDate(),
            entity.getDescription(),
            entity.getTotalAmount(),
            entity.getSharePerMember(),
            entity.getStatus(),
            entity.getSession() != null ? entity.getSession().getId() : null,
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    public static AidEntity toEntity(Aid domain) {
        if (domain == null) return null;
        AidEntity entity = new AidEntity();
        entity.setId(domain.getId());
        entity.setType(domain.getType());
        entity.setAidDate(domain.getAidDate());
        entity.setDescription(domain.getDescription());
        entity.setTotalAmount(domain.getTotalAmount());
        entity.setSharePerMember(domain.getSharePerMember());
        entity.setStatus(domain.getStatus());
        return entity;
    }
}
