package org.asvosonk.cashbox.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.asvosonk.cashbox.domain.model.Cashbox;
import org.asvosonk.cashbox.domain.repository.CashboxRepository;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;
import org.asvosonk.cashbox.infrastructure.persistence.entity.CashboxEntity;
import org.asvosonk.cashbox.infrastructure.persistence.mapper.CashboxMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Primary
@Repository
@RequiredArgsConstructor
public class JpaCashboxRepository implements CashboxRepository {

    private final SpringDataCashboxRepository springData;

    @Override
    public Optional<Cashbox> findByType(CashboxType type) {
        return springData.findByType(type).map(CashboxMapper::toDomain);
    }

    @Override
    public Cashbox save(Cashbox cashbox) {
        CashboxEntity entity = CashboxMapper.toEntity(cashbox);
        return CashboxMapper.toDomain(springData.save(entity));
    }

}
