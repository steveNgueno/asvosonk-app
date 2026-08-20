package org.asvosonk.cashbox;

import jakarta.persistence.EntityManager;
import org.asvosonk.cashbox.domain.model.CashboxMovement;
import org.asvosonk.cashbox.domain.repository.CashboxMovementRepository;
import org.asvosonk.cashbox.domain.valueobject.CashboxType;
import org.asvosonk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression: the cashbox movement history page failed with an HTTP 500 on every
 * visit — filters or not.
 *
 * <p>Its query used {@code (:type IS NULL OR ...)} predicates; PostgreSQL cannot
 * infer the type of a parameter that appears only in a bare {@code ? IS NULL}
 * position and rejected the statement with <em>"could not determine data type of
 * parameter $1"</em>. These tests exercise the four combinations (no filter, by
 * cashbox, by period, both) so the page cannot silently break again.
 */
@SpringBootTest
@Transactional
class CashboxMovementFilterIT extends AbstractIntegrationTest {

    @Autowired CashboxMovementRepository repository;
    @Autowired EntityManager em;

    @BeforeEach
    void seed() {
        insertMovement(CashboxType.development, "2026-03-10", "ancien mouvement");
        insertMovement(CashboxType.development, "2026-06-15", "mouvement du jour");
        insertMovement(CashboxType.sanction,    "2026-06-15", "paiement sanction");
        em.flush();
    }

    @Test
    void noFilterReturnsEverything() {
        List<CashboxMovement> all = repository.findFiltered(null, null, null);
        assertThat(all).hasSize(3);
    }

    @Test
    void filterByCashboxKeepsOnlyThatCashbox() {
        List<CashboxMovement> result =
            repository.findFiltered(CashboxType.sanction, null, null);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCashbox().getType()).isEqualTo(CashboxType.sanction);
    }

    @Test
    void filterByPeriodIsInclusiveOnBothBounds() {
        List<CashboxMovement> result = repository.findFiltered(
            null, LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 15));
        // Both movements of that very day are kept: the upper bound covers the
        // whole day, not only midnight.
        assertThat(result).hasSize(2);
    }

    @Test
    void filtersCombine() {
        List<CashboxMovement> result = repository.findFiltered(
            CashboxType.development, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReason()).isEqualTo("mouvement du jour");
    }

    @Test
    void resultsAreOrderedMostRecentFirst() {
        List<CashboxMovement> all = repository.findFiltered(null, null, null);
        assertThat(all).isSortedAccordingTo(
            (a, b) -> b.getMovementDate().compareTo(a.getMovementDate()));
    }

    private void insertMovement(CashboxType type, String date, String reason) {
        em.createNativeQuery("""
                INSERT INTO cashbox_movement (cashbox_id, movement_date, direction, amount, reason, origin)
                VALUES ((SELECT id FROM cashbox WHERE type = CAST(:type AS cashbox_type)),
                        CAST(:date AS timestamp), 'in', 1000, :reason, 'manual')
                """)
            .setParameter("type", type.name())
            .setParameter("date", date + " 10:30:00")
            .setParameter("reason", reason)
            .executeUpdate();
    }
}
