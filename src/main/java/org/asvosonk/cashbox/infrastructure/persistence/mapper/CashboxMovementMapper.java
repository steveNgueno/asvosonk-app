package org.asvosonk.cashbox.infrastructure.persistence.mapper;

import org.asvosonk.cashbox.domain.model.CashboxMovement;
import org.asvosonk.cashbox.infrastructure.persistence.entity.CashboxEntity;
import org.asvosonk.cashbox.infrastructure.persistence.entity.CashboxMovementEntity;

/**
 * Mapper between CashboxMovement domain model and CashboxMovementEntity JPA entity.
 */
public class CashboxMovementMapper {

    public static CashboxMovement toDomain(CashboxMovementEntity entity) {
        if (entity == null) return null;
        return new CashboxMovement(
            entity.getId(),
            CashboxMapper.toDomain(entity.getCashbox()),
            entity.getMovementDate(),
            entity.getDirection(),
            entity.getAmount(),
            entity.getReason(),
            entity.getOrigin(),
            entity.getMember() != null ? entity.getMember().getId() : null,
            entity.getSession() != null ? entity.getSession().getId() : null,
            entity.getReferenceId(),
            entity.getCreatedBy() != null ? entity.getCreatedBy().getId() : null,
            entity.getCreatedAt()
        );
    }

    public static CashboxMovementEntity toEntity(CashboxMovement domain) {
        if (domain == null) return null;
        CashboxMovementEntity entity = new CashboxMovementEntity();
        entity.setId(domain.getId());
        // Note: cashbox, member, session, createdBy relations require entity references
        // to be set by the caller when full object graph is needed
        return entity;
    }
}
