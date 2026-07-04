package org.asvosonk.tontine.infrastructure.persistence.mapper;

import org.asvosonk.common.infrastructure.persistence.EntityMapper;
import org.asvosonk.tontine.domain.model.TontineContribution;
import org.asvosonk.tontine.infrastructure.persistence.entity.TontineContributionEntity;

public class TontineContributionMapper implements EntityMapper<TontineContribution, TontineContributionEntity> {

    public static final TontineContributionMapper INSTANCE = new TontineContributionMapper();

    @Override
    public TontineContribution toDomain(TontineContributionEntity entity) {
        if (entity == null) return null;
        return new TontineContribution(
            entity.getId(),
            entity.getTourId(),
            entity.getSessionId(),
            entity.getContributorId(),
            entity.getBeneficiaryId(),
            entity.getAmount(),
            entity.getStatus(),
            entity.getCreatedAt()
        );
    }

    @Override
    public TontineContributionEntity toEntity(TontineContribution domain) {
        if (domain == null) return null;
        TontineContributionEntity entity = new TontineContributionEntity();
        entity.setId(domain.getId());
        entity.setTourId(domain.getTourId());
        entity.setSessionId(domain.getSessionId());
        entity.setContributorId(domain.getContributorId());
        entity.setBeneficiaryId(domain.getBeneficiaryId());
        entity.setAmount(domain.getAmount());
        entity.setStatus(domain.getStatus());
        entity.setCreatedAt(domain.getCreatedAt());
        return entity;
    }
}
