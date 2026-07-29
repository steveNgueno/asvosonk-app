package org.asvosonk.presence;

import jakarta.persistence.EntityManager;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.presence.application.usecase.ClosePresenceTourUseCase;
import org.asvosonk.presence.application.usecase.GetPresenceTourSummaryUseCase;
import org.asvosonk.presence.application.usecase.MarkPresenceBenefitedUseCase;
import org.asvosonk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F-08 — Presence invariants must be enforced server-side, not just hidden in
 * the UI. Covers double-serving, out-of-order serving, and premature close.
 */
@SpringBootTest
@Transactional
class PresenceInvariantsIT extends AbstractIntegrationTest {

    @Autowired MarkPresenceBenefitedUseCase markBenefited;
    @Autowired ClosePresenceTourUseCase closeTour;
    @Autowired GetPresenceTourSummaryUseCase summary;
    @Autowired EntityManager em;

    private Long tourId;
    private Long m1;   // draw_order 1
    private Long m2;   // draw_order 2

    @BeforeEach
    void seed() {
        m1 = insertMember("Presence M1");
        m2 = insertMember("Presence M2");
        tourId = ((Number) em.createNativeQuery(
                "INSERT INTO presence_tour (start_date) VALUES (CURRENT_DATE) RETURNING id")
            .getSingleResult()).longValue();
        insertParticipant(m1, 1);
        insertParticipant(m2, 2);
        em.flush();
    }

    @Test
    void servingOutOfOrderIsRejected() {
        // m2 (order 2) cannot be served before m1 (order 1)
        assertThatThrownBy(() -> markBenefited.execute(tourId, m2, null))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("ordre de passage");
    }

    @Test
    void doubleServingIsRejected() {
        markBenefited.execute(tourId, m1, null);
        em.flush();
        // Marking m1 again must fail (already benefited)
        assertThatThrownBy(() -> markBenefited.execute(tourId, m1, null))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("déjà bénéficié");
    }

    @Test
    void prematureCloseIsRejectedThenAllowedWhenComplete() {
        markBenefited.execute(tourId, m1, null);
        em.flush();

        // Only 1/2 served → manual close must be refused
        assertThatThrownBy(() -> closeTour.execute(tourId))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("n'ont pas encore bénéficié");

        // Serving the last one auto-closes the tour (happy path stays intact)
        markBenefited.execute(tourId, m2, null);
        em.flush();
        assertTourClosed();
    }

    @Test
    void inOrderServingSucceeds() {
        assertThatCode(() -> markBenefited.execute(tourId, m1, null))
            .doesNotThrowAnyException();
    }

    @Test
    void emptyTourIsNotReportedAsAllBenefited() {
        // F-57 — a tour with no participants must not be considered complete
        // (allMatch on an empty stream is vacuously true).
        Long emptyTour = ((Number) em.createNativeQuery(
                "INSERT INTO presence_tour (start_date) VALUES (CURRENT_DATE) RETURNING id")
            .getSingleResult()).longValue();
        em.flush();

        assertThat(summary.allParticipantsBenefited(emptyTour)).isFalse();
    }

    private Long insertMember(String name) {
        return ((Number) em.createNativeQuery(
                "INSERT INTO member (full_name, join_date) VALUES (:n, CURRENT_DATE) RETURNING id")
            .setParameter("n", name)
            .getSingleResult()).longValue();
    }

    private void insertParticipant(Long member, int order) {
        em.createNativeQuery(
                "INSERT INTO presence_tour_participant (tour_id, member_id, draw_order) "
              + "VALUES (:t, :m, :o)")
            .setParameter("t", tourId)
            .setParameter("m", member)
            .setParameter("o", order)
            .executeUpdate();
    }

    private void assertTourClosed() {
        em.flush();
        em.clear();
        String status = (String) em.createNativeQuery(
                "SELECT CAST(status AS text) FROM presence_tour WHERE id = :id")
            .setParameter("id", tourId)
            .getSingleResult();
        assertThat(status).isEqualTo("closed");
    }
}
