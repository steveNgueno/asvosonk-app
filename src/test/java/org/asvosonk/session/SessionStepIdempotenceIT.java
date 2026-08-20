package org.asvosonk.session;

import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.domain.repository.AppUserRepository;
import org.asvosonk.session.application.service.SessionService;
import org.asvosonk.session.application.service.SessionStepService;
import org.asvosonk.session.domain.valueobject.SessionStep;
import org.asvosonk.session.infrastructure.persistence.entity.MeetingSessionEntity;
import org.asvosonk.session.presentation.request.SessionForm;
import org.asvosonk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F-10: session step transitions must be idempotent. A stale re-post whose
 * expected step no longer matches the session's actual step is rejected,
 * so a double-click cannot replay a financial transition.
 */
@SpringBootTest
@Transactional
class SessionStepIdempotenceIT extends AbstractIntegrationTest {

    @Autowired SessionService     sessionService;
    @Autowired SessionStepService sessionStepService;
    @Autowired AppUserRepository  appUserRepository;
    @Autowired org.asvosonk.presence.application.usecase.CreatePresenceTourUseCase createPresenceTour;
    @Autowired jakarta.persistence.EntityManager em;

    /**
     * La présence ne s.ouvre plus sans tour en cours : c.est lui qui porte la
     * rotation des bénéficiaires. Les séances de test en ouvrent donc un.
     */
    @org.junit.jupiter.api.BeforeEach
    void openPresenceTour() {
        // Un tour regroupe tous les membres actifs et en demande au moins deux.
        for (String name : java.util.List.of("Step M1", "Step M2")) {
            em.createNativeQuery(
                    "INSERT INTO member (full_name, join_date) VALUES (:n, CURRENT_DATE)")
                .setParameter("n", name)
                .executeUpdate();
        }
        em.flush();
        createPresenceTour.execute(LocalDate.now().minusMonths(1));
        em.flush();
    }

    private MeetingSessionEntity freshSession() {
        AppUser admin = appUserRepository.findByLogin("admin").orElseThrow();
        SessionForm form = new SessionForm();
        form.setSessionDate(LocalDate.now());
        form.setAgenda("test");
        return sessionService.create(form, admin);
    }

    @Test
    void staleReplayIsRejected() {
        AppUser admin = appUserRepository.findByLogin("admin").orElseThrow();
        MeetingSessionEntity s = freshSession();

        // First transition CREATED -> PRESENCE_OPEN, declaring the expected step.
        sessionStepService.transitionToNext(s.getId(), admin, SessionStep.CREATED);
        assertThat(sessionService.findById(s.getId()).getCurrentStepEnum())
            .isEqualTo(SessionStep.PRESENCE_OPEN);

        // A stale re-post still claiming CREATED must be rejected (idempotence).
        assertThatThrownBy(() ->
                sessionStepService.transitionToNext(s.getId(), admin, SessionStep.CREATED))
            .isInstanceOf(BusinessRuleException.class);

        // Step unchanged after the rejected replay.
        assertThat(sessionService.findById(s.getId()).getCurrentStepEnum())
            .isEqualTo(SessionStep.PRESENCE_OPEN);
    }

    @Test
    void correctExpectedStepAdvancesNormally() {
        AppUser admin = appUserRepository.findByLogin("admin").orElseThrow();
        MeetingSessionEntity s = freshSession();

        sessionStepService.transitionToNext(s.getId(), admin, SessionStep.CREATED);

        // La clôture de la présence exige un bénéficiaire : c'est lui qui reçoit
        // la tontine et qui détermine le montant dû par chacun.
        Long beneficiary = ((Number) em.createNativeQuery(
                "SELECT member_id FROM presence_tour_participant ORDER BY draw_order LIMIT 1")
            .getSingleResult()).longValue();
        sessionService.setBeneficiary(s.getId(), beneficiary);

        // Next legitimate step declares PRESENCE_OPEN and succeeds.
        sessionStepService.transitionToNext(s.getId(), admin, SessionStep.PRESENCE_OPEN);

        assertThat(sessionService.findById(s.getId()).getCurrentStepEnum())
            .isEqualTo(SessionStep.PRESENCE_CLOSED);
    }
}
