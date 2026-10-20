package org.asvosonk.tontine;

import jakarta.persistence.EntityManager;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.domain.repository.AppUserRepository;
import org.asvosonk.session.application.service.SessionStepService;
import org.asvosonk.session.domain.valueobject.SessionStep;
import org.asvosonk.support.AbstractIntegrationTest;
import org.asvosonk.tontine.application.usecase.RecordTontineContributionUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La rubrique Grande Tontine ne se clôture que lorsque chaque participant a sa
 * ligne (montant versé, ou 0 pour un échec). Un participant oublié échappait
 * auparavant à l'échec et à sa sanction. Le bénéficiaire du jour n'est pas
 * tenu de cotiser à sa propre tontine.
 */
@SpringBootTest
@Transactional
class TontineCloseCompletenessIT extends AbstractIntegrationTest {

    private static final BigDecimal AMOUNT = new BigDecimal("5000");

    @Autowired RecordTontineContributionUseCase recordContribution;
    @Autowired SessionStepService               sessionStepService;
    @Autowired AppUserRepository                appUserRepository;
    @Autowired EntityManager                    em;

    private AppUser admin;
    private Long tourId;
    private Long sessionId;
    private Long beneficiary;
    private Long contributor;
    private Long forgotten;

    @BeforeEach
    void seed() {
        admin = appUserRepository.findByLogin("admin").orElseThrow();
        beneficiary = insertMember("Complet Bénéficiaire");
        contributor = insertMember("Complet Cotisant");
        forgotten   = insertMember("Complet Oublié");
        tourId = ((Number) em.createNativeQuery(
                "INSERT INTO tontine_tour (start_date) VALUES (CURRENT_DATE) RETURNING id")
            .getSingleResult()).longValue();
        insertParticipant(beneficiary, 1);
        insertParticipant(contributor, 2);
        insertParticipant(forgotten, 3);
        sessionId = ((Number) em.createNativeQuery("""
                INSERT INTO meeting_session (session_date, status, current_step)
                VALUES (CURRENT_DATE, 'open', 'TONTINE_OPEN') RETURNING id
                """).getSingleResult()).longValue();
        em.flush();
    }

    @Test
    void laClotureEstRefuseeTantQuUnParticipantNAPasDeLigne() {
        recordContribution.replaceInSession(tourId, sessionId, contributor, beneficiary, AMOUNT);
        em.flush();

        assertThatThrownBy(() ->
                sessionStepService.transitionToNext(sessionId, admin, SessionStep.TONTINE_OPEN))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("Complet Oublié")
            // Le bénéficiaire du jour n'est pas tenu de cotiser.
            .hasMessageNotContaining("Complet Bénéficiaire");
    }

    @Test
    void laClotureAboutitQuandChaqueParticipantASaLigne() {
        recordContribution.replaceInSession(tourId, sessionId, contributor, beneficiary, AMOUNT);
        // Échec saisi explicitement : la ligne existe.
        recordContribution.replaceInSession(tourId, sessionId, forgotten, beneficiary, BigDecimal.ZERO);
        em.flush();

        assertThatCode(() ->
                sessionStepService.transitionToNext(sessionId, admin, SessionStep.TONTINE_OPEN))
            .doesNotThrowAnyException();
    }

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
}
