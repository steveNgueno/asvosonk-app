package org.asvosonk.tontine.infrastructure.persistence.mapper;

import org.asvosonk.common.infrastructure.persistence.EntityMapper;
import org.asvosonk.tontine.domain.model.TontineParticipant;
import org.asvosonk.tontine.infrastructure.persistence.entity.TontineParticipantEntity;

public class TontineParticipantMapper implements EntityMapper<TontineParticipant, TontineParticipantEntity> {

    public static final TontineParticipantMapper INSTANCE = new TontineParticipantMapper();

    @Override
    public TontineParticipant toDomain(TontineParticipantEntity entity) {
        if (entity == null) return null;
        return new TontineParticipant(
            entity.getId(),
            entity.getTourId(),
            entity.getMemberId(),
            entity.getDrawOrder(),
            entity.isHasBenefited(),
            entity.getCreatedAt()
        );
    }

    @Override
    public TontineParticipantEntity toEntity(TontineParticipant domain) {
        if (domain == null) return null;
        TontineParticipantEntity entity = new TontineParticipantEntity();
        entity.setId(domain.getId());
        entity.setTourId(domain.getTourId());
        entity.setMemberId(domain.getMemberId());
        entity.setDrawOrder(domain.getDrawOrder());
        entity.setHasBenefited(domain.isHasBenefited());
        entity.setCreatedAt(domain.getCreatedAt());
        return entity;
    }
}
