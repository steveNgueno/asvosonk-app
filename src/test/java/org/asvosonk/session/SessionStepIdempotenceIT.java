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
        // Next legitimate step declares PRESENCE_OPEN and succeeds.
        sessionStepService.transitionToNext(s.getId(), admin, SessionStep.PRESENCE_OPEN);

        assertThat(sessionService.findById(s.getId()).getCurrentStepEnum())
            .isEqualTo(SessionStep.PRESENCE_CLOSED);
    }
}
