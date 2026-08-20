package org.asvosonk.session;

import jakarta.persistence.EntityManager;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.session.application.service.SessionService;
import org.asvosonk.session.application.usecase.GetRevolvingFundStatusUseCase;
import org.asvosonk.session.infrastructure.persistence.entity.SessionAttendanceEntity;
import org.asvosonk.session.presentation.request.AttendanceEntryForm;
import org.asvosonk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression on the presence screen.
 *
 * <p>The sheet used to post its two values through two separate forms per row:
 * the "amount" form did not carry the presence checkbox, so saving an amount
 * silently reset {@code is_present} to false — presence was in practice never
 * recorded. Both values now travel together in one entry.
 *
 * <p>Also covers {@link GetRevolvingFundStatusUseCase}, whose query feeds the
 * "situation fond de roulement" column of the same screen.
 */
@SpringBootTest
@Transactional
class AttendanceSheetIT extends AbstractIntegrationTest {

    @Autowired SessionService sessionService;
    @Autowired GetRevolvingFundStatusUseCase fundStatus;
    @Autowired EntityManager em;

    private Long sessionId;
    private Long memberId;

    @BeforeEach
    void seed() {
        memberId = ((Number) em.createNativeQuery(
                "INSERT INTO member (full_name, join_date) VALUES ('Présence Test', CURRENT_DATE) RETURNING id")
            .getSingleResult()).longValue();

        sessionId = ((Number) em.createNativeQuery("""
                INSERT INTO meeting_session (session_date, status, current_step)
                VALUES (DATE '2026-07-07', 'open', 'PRESENCE_OPEN') RETURNING id
                """).getSingleResult()).longValue();

        em.createNativeQuery("""
                INSERT INTO session_attendance (session_id, member_id, is_present, amount_paid)
                VALUES (:s, :m, false, 0)
                """)
            .setParameter("s", sessionId)
            .setParameter("m", memberId)
            .executeUpdate();
        em.flush();
    }

    @Test
    void presenceAndAmountAreSavedTogether() {
        AttendanceEntryForm entry = new AttendanceEntryForm();
        entry.setMemberId(memberId);
        entry.setPresent(true);
        entry.setAmountPaid(new BigDecimal("2000"));

        sessionService.saveAttendanceEntry(sessionId, entry);
        em.flush();
        em.clear();

        SessionAttendanceEntity saved = attendance();
        assertThat(saved.isPresent()).isTrue();
        assertThat(saved.getAmountPaid()).isEqualByComparingTo("2000");
    }

    @Test
    void savingAnAmountDoesNotSilentlyClearPresence() {
        AttendanceEntryForm present = new AttendanceEntryForm();
        present.setMemberId(memberId);
        present.setPresent(true);
        present.setAmountPaid(new BigDecimal("2000"));
        sessionService.saveAttendanceEntry(sessionId, present);

        // Correcting only the amount, while still declaring the member present.
        AttendanceEntryForm corrected = new AttendanceEntryForm();
        corrected.setMemberId(memberId);
        corrected.setPresent(true);
        corrected.setAmountPaid(new BigDecimal("1500"));
        sessionService.saveAttendanceEntry(sessionId, corrected);

        em.flush();
        em.clear();

        SessionAttendanceEntity saved = attendance();
        assertThat(saved.isPresent()).isTrue();
        assertThat(saved.getAmountPaid()).isEqualByComparingTo("1500");
    }

    /**
     * On ne donne pas plus que son dû : sans dette antérieure, le plafond est la
     * cotisation du jour.
     */
    @Test
    void unVersementSuperieurAuDuEstRefuse() {
        AttendanceEntryForm trop = new AttendanceEntryForm();
        trop.setMemberId(memberId);
        trop.setPresent(true);
        trop.setAmountPaid(new BigDecimal("4000"));

        assertThatThrownBy(() -> sessionService.saveAttendanceEntry(sessionId, trop))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("dépassent ce qui est dû");

        assertThat(sessionService.presenceCeiling(sessionId, memberId))
            .isEqualByComparingTo("2000");
    }

    @Test
    void revolvingFundStatusQueryRuns() {
        em.createNativeQuery("INSERT INTO revolving_fund (member_id, balance) VALUES (:m, 5000)")
            .setParameter("m", memberId)
            .executeUpdate();
        em.flush();

        // The query compares an enum column: rendered as a Java enum literal it
        // produced 'advance'::FundMovementType, a type that does not exist in
        // PostgreSQL, and the presence screen answered HTTP 500.
        var statuses = fundStatus.findAllByMemberId();
        assertThat(statuses).containsKey(memberId);
        assertThat(statuses.get(memberId).balance()).isEqualByComparingTo("5000");
        assertThat(statuses.get(memberId).pendingAdvances()).isZero();
        assertThat(statuses.get(memberId).isUpToDate()).isTrue();
    }

    private SessionAttendanceEntity attendance() {
        List<SessionAttendanceEntity> rows = sessionService.findAttendances(sessionId);
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }
}
