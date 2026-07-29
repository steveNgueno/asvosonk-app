package org.asvosonk.presence;

import jakarta.persistence.EntityManager;
import org.asvosonk.presence.application.usecase.GetCurrentPresenceBeneficiaryUseCase;
import org.asvosonk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F-09 — Reading the next presence beneficiary must never mutate state.
 * Even when every participant has already benefited, a peek must NOT close the
 * tour (that side effect belongs to the explicit MarkPresenceBenefited write).
 */
@SpringBootTest
@Transactional
class PeekNextBeneficiaryIT extends AbstractIntegrationTest {

    @Autowired GetCurrentPresenceBeneficiaryUseCase useCase;
    @Autowired EntityManager em;

    private Long tourId;
    private Long memberId;

    @BeforeEach
    void seed() {
        memberId = ((Number) em.createNativeQuery(
                "INSERT INTO member (full_name, join_date) VALUES ('Peek Member', CURRENT_DATE) RETURNING id")
            .getSingleResult()).longValue();
        tourId = ((Number) em.createNativeQuery(
                "INSERT INTO presence_tour (start_date) VALUES (CURRENT_DATE) RETURNING id")
            .getSingleResult()).longValue();
    }

    @Test
    void peekReturnsNextWhenSomeoneStillWaits() {
        insertParticipant(memberId, 1, false);
        em.flush();

        var next = useCase.peekNextBeneficiary();

        assertThat(next).isNotNull();
        assertThat(next.getMemberId()).isEqualTo(memberId);
        assertTourStillOpen();
    }

    @Test
    void peekDoesNotCloseTourWhenAllHaveBenefited() {
        insertParticipant(memberId, 1, true);
        em.flush();

        var next = useCase.peekNextBeneficiary();

        assertThat(next).as("no one left to benefit").isNull();
        assertTourStillOpen();
    }

    private void insertParticipant(Long member, int order, boolean benefited) {
        em.createNativeQuery(
                "INSERT INTO presence_tour_participant (tour_id, member_id, draw_order, has_benefited) "
              + "VALUES (:t, :m, :o, :b)")
            .setParameter("t", tourId)
            .setParameter("m", member)
            .setParameter("o", order)
            .setParameter("b", benefited)
            .executeUpdate();
    }

    private void assertTourStillOpen() {
        em.flush();
        em.clear();
        String status = (String) em.createNativeQuery(
                "SELECT CAST(status AS text) FROM presence_tour WHERE id = :id")
            .setParameter("id", tourId)
            .getSingleResult();
        assertThat(status).as("peek must not close the tour").isEqualTo("open");
    }
}
