package org.asvosonk.bank.infrastructure.persistence.mapper;

import org.asvosonk.bank.domain.model.Saving;
import org.asvosonk.bank.infrastructure.persistence.entity.SavingEntity;

public class SavingMapper {

    public static Saving toDomain(SavingEntity entity) {
        if (entity == null) return null;
        return new Saving(
            entity.getId(),
            entity.getMemberId(),
            entity.getOperationDate(),
            entity.getAmount(),
            entity.getSessionId(),
            entity.getCreatedAt()
        );
    }

    public static SavingEntity toEntity(Saving domain) {
        if (domain == null) return null;
        SavingEntity entity = new SavingEntity();
        entity.setId(domain.getId());
        entity.setMemberId(domain.getMemberId());
        entity.setOperationDate(domain.getOperationDate());
        entity.setAmount(domain.getAmount());
        entity.setSessionId(domain.getSessionId());
        return entity;
    }
}
