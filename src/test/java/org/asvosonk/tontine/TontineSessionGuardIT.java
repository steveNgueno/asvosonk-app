package org.asvosonk.tontine;

import jakarta.persistence.EntityManager;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.support.AbstractIntegrationTest;
import org.asvosonk.tontine.application.usecase.RecordTontineContributionUseCase;
import org.asvosonk.tontine.domain.repository.TontineContributionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Une cotisation saisie en séance n'est acceptée que pendant la rubrique Grande
 * Tontine, et au profit du bénéficiaire prévu (premier participant non servi).
 * Sans cela, le total figé à la clôture ne correspondait plus aux cotisations
 * réellement enregistrées, et un envoi forgé pouvait payer un autre participant.
 */
@SpringBootTest
@Transactional
class TontineSessionGuardIT extends AbstractIntegrationTest {

    private static final BigDecimal AMOUNT = new BigDecimal("5000");

    @Autowired RecordTontineContributionUseCase useCase;
    @Autowired TontineContributionRepository    contributionRepository;
    @Autowired EntityManager                    em;

    private Long tourId;
    private Long first;
    private Long second;
    private Long third;

    @BeforeEach
    void seed() {
        first  = insertMember("Garde Tontine 1");
        second = insertMember("Garde Tontine 2");
        third  = insertMember("Garde Tontine 3");
        tourId = ((Number) em.createNativeQuery(
                "INSERT INTO tontine_tour (start_date) VALUES (CURRENT_DATE) RETURNING id")
            .getSingleResult()).longValue();
        insertParticipant(first, 1);
        insertParticipant(second, 2);
        insertParticipant(third, 3);
        em.flush();
    }

    @Test
    void refuseeAvantLOuvertureDeLaRubrique() {
        Long sessionId = insertSession("PRESENCE_CLOSED");

        assertThatThrownBy(() -> useCase.replaceInSession(tourId, sessionId, second, first, AMOUNT))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("n'est pas ouverte");
        assertThat(contributionRepository.findBySessionId(sessionId)).isEmpty();
    }

    @Test
    void refuseeApresLaClotureDeLaRubrique() {
        Long sessionId = insertSession("TONTINE_CLOSED");

        assertThatThrownBy(() -> useCase.recordInSession(tourId, sessionId, second, first, AMOUNT))
            .isInstanceOf(BusinessRuleException.class);
        assertThat(contributionRepository.findBySessionId(sessionId)).isEmpty();
    }

    @Test
    void refuseeAuProfitDUnAutreQueLeBeneficiairePrevu() {
        Long sessionId = insertSession("TONTINE_OPEN");

        assertThatThrownBy(() -> useCase.replaceInSession(tourId, sessionId, second, third, AMOUNT))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("bénéficiaire");
        assertThat(contributionRepository.findBySessionId(sessionId)).isEmpty();
    }

    @Test
    void accepteePendantLaRubriqueAuProfitDuBeneficiairePrevu() {
        Long sessionId = insertSession("TONTINE_OPEN");

        useCase.replaceInSession(tourId, sessionId, second, first, AMOUNT);
        useCase.replaceInSession(tourId, sessionId, third, first, AMOUNT);

        assertThat(contributionRepository.findBySessionId(sessionId)).hasSize(2);
        assertThat(useCase.expectedBeneficiary(tourId, sessionId)).isEqualTo(first);
    }

    // ── seed helpers ─────────────────────────────────────────

    private Long insertMember(String name) {
        return ((Number) em.createNativeQuery(
                "INSERT INTO member (full_name, join_date) VALUES (:n, CURRENT_DATE) RETURNING id")
            .setParameter("n", name)
            .getSingleResult()).longValue();
    }

    private void insertParticipant(Long member, int drawOrder) {
        em.createNativeQuery(
                "INSERT INTO tontine_participant (tour_id, member_id, draw_order) VALUES (:t, :m, :o)")
            .setParameter("t", tourId)
            .setParameter("m", member)
            .setParameter("o", drawOrder)
            .executeUpdate();
    }

    private Long insertSession(String step) {
        return ((Number) em.createNativeQuery("""
                INSERT INTO meeting_session (session_date, status, current_step)
                VALUES (DATE '2026-07-21', 'open', :step) RETURNING id
                """)
            .setParameter("step", step)
            .getSingleResult()).longValue();
    }
}
