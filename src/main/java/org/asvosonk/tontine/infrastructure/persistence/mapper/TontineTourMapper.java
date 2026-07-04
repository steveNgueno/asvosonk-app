package org.asvosonk.tontine.infrastructure.persistence.mapper;

import org.asvosonk.common.infrastructure.persistence.EntityMapper;
import org.asvosonk.tontine.domain.model.TontineTour;
import org.asvosonk.tontine.infrastructure.persistence.entity.TontineTourEntity;

public class TontineTourMapper implements EntityMapper<TontineTour, TontineTourEntity> {

    public static final TontineTourMapper INSTANCE = new TontineTourMapper();

    @Override
    public TontineTour toDomain(TontineTourEntity entity) {
        if (entity == null) return null;
        return new TontineTour(
            entity.getId(),
            entity.getStartDate(),
            entity.getEndDate(),
            entity.getStatus(),
            entity.getCreatedAt()
        );
    }

    @Override
    public TontineTourEntity toEntity(TontineTour domain) {
        if (domain == null) return null;
        TontineTourEntity entity = new TontineTourEntity();
        entity.setId(domain.getId());
        entity.setStartDate(domain.getStartDate());
        entity.setEndDate(domain.getEndDate());
        entity.setStatus(domain.getStatus());
        entity.setCreatedAt(domain.getCreatedAt());
        return entity;
    }
}
