package org.asvosonk.session;

import jakarta.persistence.EntityManager;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.security.application.service.UserDetailsImpl;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.domain.model.Permission;
import org.asvosonk.security.domain.model.Role;
import org.asvosonk.session.application.service.SessionService;
import org.asvosonk.session.presentation.request.AttendanceEntryForm;
import org.asvosonk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La feuille de présence et le bénéficiaire ne se modifient que pendant la
 * saisie de présence ; la feuille n'accepte que des membres actifs ; et faire
 * avancer une séance demande une permission d'écriture, pas de consultation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PresenceStepGuardIT extends AbstractIntegrationTest {

    @Autowired SessionService sessionService;
    @Autowired MockMvc        mockMvc;
    @Autowired EntityManager  em;

    private Long activeMember;
    private Long otherMember;

    @BeforeEach
    void seed() {
        activeMember = insertMember("Garde Actif", "active");
        otherMember  = insertMember("Garde Autre", "active");
    }

    @Test
    void laFeuilleEstFigeeApresLaClotureDeLaPresence() {
        Long sessionId = insertSession("PRESENCE_CLOSED");
        insertAttendance(sessionId, activeMember);

        assertThatThrownBy(() -> sessionService.saveAttendanceEntry(sessionId, entry(activeMember, "2000")))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("n'est plus modifiable");

        em.flush();
        em.clear();
        BigDecimal paid = (BigDecimal) em.createNativeQuery(
                "SELECT amount_paid FROM session_attendance WHERE session_id = :s AND member_id = :m")
            .setParameter("s", sessionId).setParameter("m", activeMember)
            .getSingleResult();
        assertThat(paid).isEqualByComparingTo("0");
    }

    @Test
    void leBeneficiaireNeChangePlusApresLaCloture() {
        Long sessionId = insertSession("PRESENCE_CLOSED");

        assertThatThrownBy(() -> sessionService.setBeneficiary(sessionId, otherMember))
            .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void leBeneficiaireSeDesignePendantLaSaisie() {
        Long sessionId = insertSession("PRESENCE_OPEN");

        sessionService.setBeneficiary(sessionId, otherMember);

        em.flush();
        em.clear();
        assertThat(sessionService.findById(sessionId).getPresenceBeneficiary().getId())
            .isEqualTo(otherMember);
    }

    @Test
    void unMembreSuspenduNePeutPasEtreAjouteALaFeuille() {
        Long sessionId = insertSession("PRESENCE_OPEN");
        Long suspended = insertMember("Garde Suspendu", "suspended");

        assertThatThrownBy(() -> sessionService.saveAttendanceEntry(sessionId, entry(suspended, "2000")))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("n'est pas un membre actif");
        assertThatThrownBy(() -> sessionService.saveAttendanceEntry(sessionId, entry(999_999L, "0")))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("introuvable");
    }

    @Test
    void unAuditeurNeFaitPasAvancerLaSeance() throws Exception {
        Long sessionId = insertSession("TONTINE_CLOSED");
        UserDetailsImpl auditor = principalWith("SESSION_VIEW", "TONTINE_VIEW", "BANK_VIEW");

        for (String step : new String[] {"tontine/open", "banque-projet/open", "banque-annuelle/open"}) {
            mockMvc.perform(post("/sessions/{id}/" + step, sessionId)
                    .with(user(auditor))
                    .with(csrf()))
                .andExpect(status().isForbidden());
        }

        em.clear();
        assertThat(sessionService.findById(sessionId).getCurrentStepEnum().name())
            .isEqualTo("TONTINE_CLOSED");
    }

    // ── seed helpers ─────────────────────────────────────────

    private static AttendanceEntryForm entry(Long memberId, String amount) {
        AttendanceEntryForm entry = new AttendanceEntryForm();
        entry.setMemberId(memberId);
        entry.setPresent(true);
        entry.setAmountPaid(new BigDecimal(amount));
        return entry;
    }

    private static UserDetailsImpl principalWith(String... permissionCodes) {
        Set<Permission> perms = Arrays.stream(permissionCodes)
            .map(code -> new Permission(null, code, code))
            .collect(Collectors.toSet());
        Role role = new Role(4, "AUDITOR", "test", perms);
        return new UserDetailsImpl(new AppUser(1L, "auditeur", "hash", role, null,
            true, null, 0, null, null, null));
    }

    private Long insertMember(String name, String status) {
        return ((Number) em.createNativeQuery("""
                INSERT INTO member (full_name, join_date, status)
                VALUES (:n, CURRENT_DATE, CAST(:s AS member_status)) RETURNING id
                """)
            .setParameter("n", name)
            .setParameter("s", status)
            .getSingleResult()).longValue();
    }

    private Long insertSession(String step) {
        return ((Number) em.createNativeQuery("""
                INSERT INTO meeting_session (session_date, status, current_step)
                VALUES (DATE '2026-07-14', 'open', :step) RETURNING id
                """)
            .setParameter("step", step)
            .getSingleResult()).longValue();
    }

    private void insertAttendance(Long sessionId, Long memberId) {
        em.createNativeQuery("""
                INSERT INTO session_attendance (session_id, member_id, is_present, amount_paid)
                VALUES (:s, :m, false, 0)
                """)
            .setParameter("s", sessionId)
            .setParameter("m", memberId)
            .executeUpdate();
        em.flush();
    }
}
