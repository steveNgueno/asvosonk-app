package org.asvosonk.tontine.infrastructure.persistence.mapper;

import org.asvosonk.common.infrastructure.persistence.EntityMapper;
import org.asvosonk.tontine.domain.model.TontineDebt;
import org.asvosonk.tontine.infrastructure.persistence.entity.TontineDebtEntity;

public class TontineDebtMapper implements EntityMapper<TontineDebt, TontineDebtEntity> {

    public static final TontineDebtMapper INSTANCE = new TontineDebtMapper();

    @Override
    public TontineDebt toDomain(TontineDebtEntity entity) {
        if (entity == null) return null;
        return new TontineDebt(
            entity.getId(),
            entity.getTourId(),
            entity.getDebtorId(),
            entity.getCreditorId(),
            entity.getAmount(),
            entity.getOriginSessionId(),
            entity.getStatus(),
            entity.getRepaymentSessionId(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    @Override
    public TontineDebtEntity toEntity(TontineDebt domain) {
        if (domain == null) return null;
        TontineDebtEntity entity = new TontineDebtEntity();
        entity.setId(domain.getId());
        entity.setTourId(domain.getTourId());
        entity.setDebtorId(domain.getDebtorId());
        entity.setCreditorId(domain.getCreditorId());
        entity.setAmount(domain.getAmount());
        entity.setOriginSessionId(domain.getOriginSessionId());
        entity.setStatus(domain.getStatus());
        entity.setRepaymentSessionId(domain.getRepaymentSessionId());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }
}
