package org.asvosonk.session.infrastructure.persistence.mapper;

import org.asvosonk.session.domain.model.SessionAttendance;
import org.asvosonk.session.infrastructure.persistence.entity.SessionAttendanceEntity;

/**
 * Mapper between SessionAttendance domain model and SessionAttendanceEntity JPA entity.
 */
public class SessionAttendanceMapper {

    public static SessionAttendance toDomain(SessionAttendanceEntity entity) {
        if (entity == null) return null;
        return new SessionAttendance(
            entity.getId(),
            entity.getSession().getId(),
            entity.getMember().getId(),
            entity.isPresent(),
            entity.getAmountPaid(),
            entity.isCoveredByFund(),
            entity.getAttendanceStatus(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    public static SessionAttendanceEntity toEntity(SessionAttendance domain) {
        if (domain == null) return null;
        SessionAttendanceEntity entity = new SessionAttendanceEntity();
        entity.setId(domain.getId());
        entity.setPresent(domain.isPresent());
        entity.setAmountPaid(domain.getAmountPaid());
        entity.setCoveredByFund(domain.isCoveredByFund());
        entity.setAttendanceStatus(domain.getAttendanceStatus());
        return entity;
    }
}
