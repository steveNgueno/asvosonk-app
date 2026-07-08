package org.asvosonk.session.infrastructure.persistence.mapper;

import org.asvosonk.session.domain.model.MeetingSession;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;

/**
 * Mapper between MeetingSession domain model and MeetingSessionEntity JPA entity.
 */
public class MeetingSessionMapper {

    public static MeetingSession toDomain(MeetingSessionEntity entity) {
        if (entity == null) return null;
        return new MeetingSession(
            entity.getId(),
            entity.getSessionDate(),
            entity.getStatus(),
            entity.getAgenda(),
            entity.getPresenceBeneficiary() != null ? entity.getPresenceBeneficiary().getId() : null,
            entity.getClosedAt(),
            entity.getCreatedBy() != null ? entity.getCreatedBy().getId() : null,
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    public static MeetingSessionEntity toEntity(MeetingSession domain) {
        if (domain == null) return null;
        MeetingSessionEntity entity = new MeetingSessionEntity();
        entity.setId(domain.getId());
        entity.setSessionDate(domain.getSessionDate());
        entity.setStatus(domain.getStatus());
        entity.setAgenda(domain.getAgenda());
        entity.setClosedAt(domain.getClosedAt());
        return entity;
    }
}
