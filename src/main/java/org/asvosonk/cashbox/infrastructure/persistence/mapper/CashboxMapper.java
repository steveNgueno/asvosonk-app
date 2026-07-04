package org.asvosonk.cashbox.infrastructure.persistence.mapper;

import org.asvosonk.cashbox.domain.model.Cashbox;
import org.asvosonk.cashbox.infrastructure.persistence.entity.CashboxEntity;

/**
 * Mapper between Cashbox domain model and CashboxEntity JPA entity.
 */
public class CashboxMapper {

    public static Cashbox toDomain(CashboxEntity entity) {
        if (entity == null) return null;
        return new Cashbox(
            entity.getId(),
            entity.getType(),
            entity.getBalance(),
            entity.getUpdatedAt()
        );
    }

    public static CashboxEntity toEntity(Cashbox domain) {
        if (domain == null) return null;
        CashboxEntity entity = new CashboxEntity();
        entity.setId(domain.getId());
        entity.setType(domain.getType());
        entity.setBalance(domain.getBalance());
        return entity;
    }
}
