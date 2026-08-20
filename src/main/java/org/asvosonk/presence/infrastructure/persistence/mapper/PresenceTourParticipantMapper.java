package org.asvosonk.presence.infrastructure.persistence.mapper;

import org.asvosonk.common.infrastructure.persistence.EntityMapper;
import org.asvosonk.presence.domain.model.PresenceTourParticipant;
import org.asvosonk.presence.infrastructure.persistence.entity.PresenceTourParticipantEntity;

public class PresenceTourParticipantMapper implements EntityMapper<PresenceTourParticipant, PresenceTourParticipantEntity> {

    public static final PresenceTourParticipantMapper INSTANCE = new PresenceTourParticipantMapper();

    @Override
    public PresenceTourParticipant toDomain(PresenceTourParticipantEntity entity) {
        if (entity == null) return null;
        return new PresenceTourParticipant(
            entity.getId(),
            entity.getTourId(),
            entity.getMemberId(),
            entity.getDrawOrder(),
            entity.isHasBenefited(),
            entity.getSessionId(),
            entity.getJoinedAt(),
            entity.isJoinedMidTour(),
            entity.getCreatedAt()
        );
    }

    @Override
    public PresenceTourParticipantEntity toEntity(PresenceTourParticipant domain) {
        if (domain == null) return null;
        PresenceTourParticipantEntity entity = new PresenceTourParticipantEntity();
        entity.setId(domain.getId());
        entity.setTourId(domain.getTourId());
        entity.setMemberId(domain.getMemberId());
        entity.setDrawOrder(domain.getDrawOrder());
        entity.setHasBenefited(domain.isHasBenefited());
        entity.setSessionId(domain.getSessionId());
        entity.setJoinedAt(domain.getJoinedAt());
        entity.setJoinedMidTour(domain.isJoinedMidTour());
        entity.setCreatedAt(domain.getCreatedAt());
        return entity;
    }
}
