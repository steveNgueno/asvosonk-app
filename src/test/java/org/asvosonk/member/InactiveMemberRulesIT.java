package org.asvosonk.member;

import jakarta.persistence.EntityManager;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.member.application.usecase.RecordFeePaymentUseCase;
import org.asvosonk.member.domain.valueobject.FeeType;
import org.asvosonk.sanction.application.usecase.CreateSanctionUseCase;
import org.asvosonk.sanction.domain.valueobject.SanctionOrigin;
import org.asvosonk.security.application.service.UserDetailsImpl;
import org.asvosonk.security.application.usecase.CreateUserUseCase;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.domain.model.Permission;
import org.asvosonk.security.domain.model.Role;
import org.asvosonk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Statut des membres : un nouveau membre est toujours actif, et un membre non
 * actif ne règle plus de frais, ne reçoit plus de sanction disciplinaire et ne
 * se voit plus ouvrir de compte utilisateur.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InactiveMemberRulesIT extends AbstractIntegrationTest {

    @Autowired RecordFeePaymentUseCase recordFee;
    @Autowired CreateSanctionUseCase   createSanction;
    @Autowired CreateUserUseCase       createUser;
    @Autowired MockMvc                 mockMvc;
    @Autowired EntityManager           em;

    private Long suspended;
    private Long active;

    @BeforeEach
    void seed() {
        suspended = insertMember("Statut Suspendu", "suspended");
        active    = insertMember("Statut Actif", "active");
        em.createNativeQuery("""
                INSERT INTO meeting_session (session_date, status, current_step)
                VALUES (CURRENT_DATE, 'open', 'CREATED')
                """).executeUpdate();
        em.flush();
    }

    @Test
    void unNouveauMembreEstToujoursActifMemeAvecUnStatutForge() throws Exception {
        Set<Permission> perms = Set.of(new Permission(null, "MEMBER_CREATE", "MEMBER_CREATE"));
        var principal = new UserDetailsImpl(new AppUser(1L, "tester", "hash",
            new Role(1, "TEST", "test", perms), null, true, null, 0, null, null, null));

        mockMvc.perform(post("/members/new")
                .param("fullName", "Statut Forgé")
                .param("joinDate", "2024-01-01")
                .param("status", "deceased")
                .with(user(principal))
                .with(csrf()));

        Object status = em.createNativeQuery(
                "SELECT CAST(status AS text) FROM member WHERE full_name = 'Statut Forgé'")
            .getSingleResult();
        assertThat(status).isEqualTo("active");
    }

    @Test
    void unMembreSuspenduNeRegleplusDeFrais() {
        assertThatThrownBy(() -> recordFee.execute(suspended, FeeType.registration, new BigDecimal("2500"), null))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("n'est pas un membre actif");
        assertThatCode(() -> recordFee.execute(active, FeeType.registration, new BigDecimal("2500"), null))
            .doesNotThrowAnyException();
    }

    @Test
    void uneSanctionManuelleNeViseQuUnMembreActif() {
        assertThatThrownBy(() -> createSanction.execute(suspended, LocalDate.now(),
                new BigDecimal("1000"), "Retard", SanctionOrigin.manual, null))
            .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> createSanction.execute(999_999L, LocalDate.now(),
                new BigDecimal("1000"), "Retard", SanctionOrigin.manual, null))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("introuvable");
        // Une sanction automatique naît d'une obligation antérieure : elle reste due.
        assertThatCode(() -> createSanction.execute(suspended, LocalDate.now(),
                new BigDecimal("2000"), "Défaut", SanctionOrigin.tontine_default, null))
            .doesNotThrowAnyException();
    }

    @Test
    void aucunCompteUtilisateurPourUnMembreNonActif() {
        assertThatThrownBy(() -> createUser.createUser("statut.suspendu", "Secret@2026", "AUDITOR", suspended))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("n'est pas un membre actif");
        assertThatThrownBy(() -> createUser.createUser("sans.membre", "Secret@2026", "AUDITOR", null))
            .isInstanceOf(BusinessRuleException.class);
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
}
