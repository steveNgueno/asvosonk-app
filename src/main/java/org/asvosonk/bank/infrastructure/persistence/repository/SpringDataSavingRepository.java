package org.asvosonk.bank.infrastructure.persistence.repository;

import org.asvosonk.bank.infrastructure.persistence.entity.SavingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface SpringDataSavingRepository extends JpaRepository<SavingEntity, Long> {
    List<SavingEntity> findByMemberIdOrderByOperationDateDesc(Long memberId);

    @Query("SELECT SUM(s.amount) FROM SavingEntity s WHERE s.memberId = :memberId")
    BigDecimal sumAmountByMemberId(Long memberId);

    List<SavingEntity> findBySessionIdOrderByIdAsc(Long sessionId);

    @Query("SELECT SUM(s.amount) FROM SavingEntity s WHERE s.sessionId = :sessionId")
    BigDecimal sumAmountBySessionId(Long sessionId);
}
