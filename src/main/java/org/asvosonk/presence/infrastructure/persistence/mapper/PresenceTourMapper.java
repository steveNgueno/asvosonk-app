package org.asvosonk.presence.infrastructure.persistence.mapper;

import org.asvosonk.common.infrastructure.persistence.EntityMapper;
import org.asvosonk.presence.domain.model.PresenceTour;
import org.asvosonk.presence.infrastructure.persistence.entity.PresenceTourEntity;

public class PresenceTourMapper implements EntityMapper<PresenceTour, PresenceTourEntity> {

    public static final PresenceTourMapper INSTANCE = new PresenceTourMapper();

    @Override
    public PresenceTour toDomain(PresenceTourEntity entity) {
        if (entity == null) return null;
        return new PresenceTour(
            entity.getId(),
            entity.getStartDate(),
            entity.getEndDate(),
            entity.getStatus(),
            entity.getCreatedAt()
        );
    }

    @Override
    public PresenceTourEntity toEntity(PresenceTour domain) {
        if (domain == null) return null;
        PresenceTourEntity entity = new PresenceTourEntity();
        entity.setId(domain.getId());
        entity.setStartDate(domain.getStartDate());
        entity.setEndDate(domain.getEndDate());
        entity.setStatus(domain.getStatus());
        entity.setCreatedAt(domain.getCreatedAt());
        return entity;
    }
}
