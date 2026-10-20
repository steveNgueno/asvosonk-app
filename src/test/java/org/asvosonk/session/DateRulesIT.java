package org.asvosonk.session;

import jakarta.persistence.EntityManager;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.presence.application.usecase.ClosePresenceTourUseCase;
import org.asvosonk.presence.application.usecase.CreatePresenceTourUseCase;
import org.asvosonk.presence.domain.repository.PresenceTourRepository;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.domain.repository.AppUserRepository;
import org.asvosonk.session.application.service.SessionService;
import org.asvosonk.session.presentation.request.SessionForm;
import org.asvosonk.support.AbstractIntegrationTest;
import org.asvosonk.tontine.application.usecase.CreateTourUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dates : une séance se crée le jour où elle se tient, après la précédente ; un
 * tour ne démarre pas dans le futur ni avant la fin du tour précédent ; un tour
 * de présence s'achève à la séance de son dernier bénéficiaire.
 */
@SpringBootTest
@Transactional
class DateRulesIT extends AbstractIntegrationTest {

    @Autowired SessionService            sessionService;
    @Autowired CreatePresenceTourUseCase createPresenceTour;
    @Autowired ClosePresenceTourUseCase  closePresenceTour;
    @Autowired PresenceTourRepository    presenceTourRepository;
    @Autowired CreateTourUseCase         createTontineTour;
    @Autowired AppUserRepository         appUserRepository;
    @Autowired EntityManager             em;

    private AppUser admin;

    @BeforeEach
    void seed() {
        admin = appUserRepository.findByLogin("admin").orElseThrow();
    }

    @Test
    void uneSeanceNeSeCreePasDansLeFutur() {
        assertThatThrownBy(() -> sessionService.create(form(LocalDate.now().plusDays(1)), admin))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("futur");
    }

    @Test
    void uneSeanceNeSIntercalePasAvantLaDerniere() {
        insertSession("2026-05-10");

        assertThatThrownBy(() -> sessionService.create(form(LocalDate.of(2026, 5, 3)), admin))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("2026-05-10");
    }

    @Test
    void unTourNeDemarrePasDansLeFutur() {
        assertThatThrownBy(() -> createPresenceTour.execute(LocalDate.now().plusDays(1)))
            .isInstanceOf(BusinessRuleException.class);
        Long a = insertMember("Date A");
        Long b = insertMember("Date B");
        assertThatThrownBy(() -> createTontineTour.execute(LocalDate.now().plusDays(1), List.of(a, b), List.of(1, 2)))
            .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void unTourDePresenceNeDemarrePasAvantLaFinDuPrecedent() {
        em.createNativeQuery("""
                INSERT INTO presence_tour (start_date, end_date, status)
                VALUES (DATE '2026-03-02', DATE '2026-06-01', 'closed')
                """).executeUpdate();
        em.flush();

        assertThatThrownBy(() -> createPresenceTour.execute(LocalDate.of(2026, 5, 15)))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("2026-06-01");
    }

    @Test
    void unTourDePresenceSAcheveALaSeanceDeSonDernierBeneficiaire() {
        Long tourId = ((Number) em.createNativeQuery(
                "INSERT INTO presence_tour (start_date) VALUES (DATE '2026-04-01') RETURNING id")
            .getSingleResult()).longValue();
        Long s1 = insertSession("2026-04-06");
        Long s2 = insertSession("2026-04-13");
        insertServedParticipant(tourId, insertMember("Servi 1"), 1, s1);
        insertServedParticipant(tourId, insertMember("Servi 2"), 2, s2);
        em.flush();

        closePresenceTour.execute(tourId);

        assertThat(presenceTourRepository.findById(tourId).orElseThrow().getEndDate())
            .isEqualTo(LocalDate.of(2026, 4, 13));
    }

    // ── seed helpers ─────────────────────────────────────────

    private static SessionForm form(LocalDate date) {
        SessionForm form = new SessionForm();
        form.setSessionDate(date);
        return form;
    }

    private Long insertSession(String date) {
        return ((Number) em.createNativeQuery(
                "INSERT INTO meeting_session (session_date, status) "
              + "VALUES (CAST(:d AS date), CAST('closed' AS session_status)) RETURNING id")
            .setParameter("d", date)
            .getSingleResult()).longValue();
    }

    private Long insertMember(String name) {
        return ((Number) em.createNativeQuery(
                "INSERT INTO member (full_name, join_date) VALUES (:n, CURRENT_DATE) RETURNING id")
            .setParameter("n", name)
            .getSingleResult()).longValue();
    }

    private void insertServedParticipant(Long tourId, Long memberId, int order, Long sessionId) {
        em.createNativeQuery("""
                INSERT INTO presence_tour_participant (tour_id, member_id, draw_order, has_benefited, session_id)
                VALUES (:t, :m, :o, true, :s)
                """)
            .setParameter("t", tourId)
            .setParameter("m", memberId)
            .setParameter("o", order)
            .setParameter("s", sessionId)
            .executeUpdate();
    }
}
