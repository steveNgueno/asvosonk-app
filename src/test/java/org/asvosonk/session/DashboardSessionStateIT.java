package org.asvosonk.session;

import org.asvosonk.session.domain.model.MeetingSession;
import org.asvosonk.session.domain.repository.MeetingSessionRepository;
import org.asvosonk.session.domain.valueobject.SessionStatus;
import org.asvosonk.session.domain.valueobject.SessionStep;
import org.asvosonk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression test for F-01: the dashboard must derive "session in progress" and
 * "last closed session" from {@code currentStep} (the live workflow state), not
 * from {@code SessionStatus} (a dead state machine that is always left at
 * {@code open}).
 *
 * <p>The application creates every session with {@code SessionStatus.open} and
 * never moves it to {@code closed}. So as soon as a second session exists,
 * {@code findByStatus(open)} — which returns an {@code Optional} — matches more
 * than one row and throws {@link IncorrectResultSizeDataAccessException},
 * crashing the dashboard (HTTP 500). This test reproduces that root cause and
 * verifies the corrected, {@code currentStep}-based selection.
 */
@SpringBootTest
@Transactional
class DashboardSessionStateIT extends AbstractIntegrationTest {

    @Autowired
    private MeetingSessionRepository repository;

    private MeetingSession newSession(LocalDate date, SessionStep step) {
        // status is intentionally 'open' for every row: that is exactly what the
        // application does today and what makes findByStatus(open) non-unique.
        return new MeetingSession(
            null, date, SessionStatus.open, "agenda",
            null, null, null, null, null, step);
    }

    @Test
    void oldStatusBasedLookupIsNonUnique_reproducesF01() {
        repository.save(newSession(LocalDate.of(2026, 1, 1), SessionStep.CREATED));
        repository.save(newSession(LocalDate.of(2026, 1, 8), SessionStep.TONTINE_OPEN));

        // The pre-fix dashboard call: two 'open' rows -> not a single result.
        assertThatThrownBy(() -> repository.findByStatus(SessionStatus.open))
            .isInstanceOf(IncorrectResultSizeDataAccessException.class);
    }

    @Test
    void currentStepDrivesInProgressAndClosedSelection() {
        var closed     = repository.save(newSession(LocalDate.of(2026, 2, 1),  SessionStep.REPORT_GENERATED));
        var older      = repository.save(newSession(LocalDate.of(2026, 2, 8),  SessionStep.TONTINE_OPEN));
        var mostRecent = repository.save(newSession(LocalDate.of(2026, 2, 15), SessionStep.CREATED));

        List<MeetingSession> all = repository.findAllByOrderBySessionDateDesc();

        // "Session in progress" = most recent whose workflow is not finished.
        MeetingSession inProgress = all.stream()
            .filter(s -> s.getCurrentStep() != SessionStep.REPORT_GENERATED)
            .findFirst().orElse(null);
        assertThat(inProgress).isNotNull();
        assertThat(inProgress.getId()).isEqualTo(mostRecent.getId());

        // "Last closed session" = most recent whose workflow reached the report.
        MeetingSession lastClosed = all.stream()
            .filter(s -> s.getCurrentStep() == SessionStep.REPORT_GENERATED)
            .findFirst().orElse(null);
        assertThat(lastClosed).isNotNull();
        assertThat(lastClosed.getId()).isEqualTo(closed.getId());

        assertThat(older.getId()).isNotIn(inProgress.getId(), lastClosed.getId());
    }
}
