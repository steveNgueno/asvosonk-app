package org.asvosonk.cashbox.infrastructure.persistence.repository;

import org.asvosonk.cashbox.domain.valueobject.CashboxType;
import org.asvosonk.cashbox.infrastructure.persistence.entity.CashboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpringDataCashboxRepository extends JpaRepository<CashboxEntity, Integer> {
    Optional<CashboxEntity> findByType(CashboxType type);
}
