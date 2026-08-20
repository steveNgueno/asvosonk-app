package org.asvosonk.cashbox.infrastructure.persistence.repository;

import org.asvosonk.cashbox.infrastructure.persistence.entity.CashboxMovementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataCashboxMovementRepository extends JpaRepository<CashboxMovementEntity, Long> {

    // Le filtrage par caisse/période est construit avec l'API Criteria dans
    // JpaCashboxMovementRepository : la variante JPQL « (:param IS NULL OR …) »
    // ne peut pas être typée par PostgreSQL et échouait à l'exécution.

    @Query("SELECT m FROM CashboxMovementEntity m JOIN FETCH m.cashbox WHERE m.session.id = :sessionId ORDER BY m.movementDate ASC")
    List<CashboxMovementEntity> findBySessionIdOrderByMovementDateAsc(Long sessionId);

    @Query("SELECT m FROM CashboxMovementEntity m JOIN FETCH m.cashbox WHERE m.cashbox.id = :cashboxId ORDER BY m.movementDate DESC")
    List<CashboxMovementEntity> findByCashboxIdOrderByMovementDateDesc(Integer cashboxId);

    /**
     * Most recent movements, limited in SQL.
     *
     * <p>The previous {@code findTop10By...} declared an explicit {@code @Query},
     * which cancels the derived "Top10" keyword: every movement ever recorded was
     * loaded and then trimmed in Java. Paging the query keeps the limit in the
     * database where it belongs.
     */
    @Query("SELECT m FROM CashboxMovementEntity m JOIN FETCH m.cashbox ORDER BY m.movementDate DESC")
    List<CashboxMovementEntity> findRecent(org.springframework.data.domain.Pageable pageable);

    @Query("SELECT m FROM CashboxMovementEntity m JOIN FETCH m.cashbox ORDER BY m.movementDate DESC")
    List<CashboxMovementEntity> findAllByOrderByMovementDateDesc();
}
