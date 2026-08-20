package org.asvosonk.session;

import org.asvosonk.session.domain.model.MeetingSession;
import org.asvosonk.session.domain.repository.MeetingSessionRepository;
import org.asvosonk.session.domain.valueobject.SessionStatus;
import org.asvosonk.session.domain.valueobject.SessionStep;
import org.asvosonk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * État des séances vu du tableau de bord (F-01).
 *
 * <p>La séance en cours et la dernière séance close se lisent sur
 * {@code currentStep}, l'avancement réel du déroulé, et non sur
 * {@code SessionStatus} : ce dernier ne distingue que « terminée ou non ».</p>
 *
 * <p>Deux séances non clôturées ne peuvent plus coexister — la base l'interdit —
 * si bien que la cause de F-01, plusieurs lignes {@code open} simultanées, est
 * désormais hors d'atteinte. Le test vérifie les deux : l'invariant, et la
 * sélection fondée sur l'étape.</p>
 */
@SpringBootTest
@Transactional
class DashboardSessionStateIT extends AbstractIntegrationTest {

    @Autowired
    private MeetingSessionRepository repository;

    private MeetingSession newSession(LocalDate date, SessionStatus status, SessionStep step) {
        return new MeetingSession(
            null, date, status, "agenda",
            null, null, null, null, null, step);
    }

    @Test
    void deuxSeancesNonClotureesNePeuventPasCoexister() {
        repository.save(newSession(LocalDate.of(2026, 1, 1), SessionStatus.open, SessionStep.CREATED));

        assertThatThrownBy(() -> repository.save(
                newSession(LocalDate.of(2026, 1, 8), SessionStatus.open, SessionStep.CREATED)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void currentStepDrivesInProgressAndClosedSelection() {
        var closed     = repository.save(newSession(LocalDate.of(2026, 2, 1),
            SessionStatus.closed, SessionStep.REPORT_GENERATED));
        var older      = repository.save(newSession(LocalDate.of(2026, 2, 8),
            SessionStatus.closed, SessionStep.REPORT_GENERATED));
        var mostRecent = repository.save(newSession(LocalDate.of(2026, 2, 15),
            SessionStatus.open, SessionStep.CREATED));

        List<MeetingSession> all = repository.findAllByOrderBySessionDateDesc();

        // « Séance en cours » = la plus récente dont le déroulé n'est pas fini.
        MeetingSession inProgress = all.stream()
            .filter(s -> s.getCurrentStep() != SessionStep.REPORT_GENERATED)
            .findFirst().orElse(null);
        assertThat(inProgress).isNotNull();
        assertThat(inProgress.getId()).isEqualTo(mostRecent.getId());

        // « Dernière séance close » = la plus récente arrivée au rapport.
        MeetingSession lastClosed = all.stream()
            .filter(s -> s.getCurrentStep() == SessionStep.REPORT_GENERATED)
            .findFirst().orElse(null);
        assertThat(lastClosed).isNotNull();
        assertThat(lastClosed.getId()).isEqualTo(older.getId());

        assertThat(closed.getId()).isNotIn(inProgress.getId(), lastClosed.getId());
    }
}
