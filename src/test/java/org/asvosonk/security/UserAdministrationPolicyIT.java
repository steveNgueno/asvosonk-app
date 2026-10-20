package org.asvosonk.security;

import jakarta.persistence.EntityManager;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.security.application.service.UserAdministrationPolicy;
import org.asvosonk.security.application.service.UserDetailsImpl;
import org.asvosonk.security.application.usecase.AssignRoleUseCase;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.domain.repository.AppUserRepository;
import org.asvosonk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Administration des comptes : SECRETARY, TREASURER et PRESIDENT ont les mêmes
 * permissions, les règles portent donc sur le rôle PRESIDENT. Un SECRETARY ne
 * doit ni changer le mot de passe d'autrui, ni se promouvoir, ni créer ou
 * modifier un compte PRESIDENT ; le dernier PRESIDENT actif ne peut pas être
 * rétrogradé.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserAdministrationPolicyIT extends AbstractIntegrationTest {

    @Autowired UserAdministrationPolicy policy;
    @Autowired AssignRoleUseCase        assignRoleUseCase;
    @Autowired AppUserRepository        appUserRepository;
    @Autowired MockMvc                  mockMvc;
    @Autowired EntityManager            em;

    private AppUser president;
    private AppUser secretary;
    private AppUser treasurer;

    @BeforeEach
    void seed() {
        president = appUserRepository.findByLogin("admin").orElseThrow();
        secretary = insertUser("policy.secretary", "SECRETARY");
        treasurer = insertUser("policy.treasurer", "TREASURER");
    }

    @Test
    void unSecretaireNeChangePasLeMotDePasseDAutrui() {
        assertThatThrownBy(() -> policy.requireCanChangePassword(secretary.getId(), president.getId()))
            .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> policy.requireCanChangePassword(secretary.getId(), treasurer.getId()))
            .isInstanceOf(BusinessRuleException.class);
        assertThatCode(() -> policy.requireCanChangePassword(secretary.getId(), secretary.getId()))
            .doesNotThrowAnyException();
        assertThatCode(() -> policy.requireCanChangePassword(president.getId(), secretary.getId()))
            .doesNotThrowAnyException();
    }

    @Test
    void leFormulaireDeMotDePasseRefuseUnSecretaireSurLeCompteAdmin() throws Exception {
        String hashBefore = president.getPasswordHash();

        mockMvc.perform(post("/users/{id}/password", president.getId())
                .param("password", "Hacked@2026")
                .param("confirmPassword", "Hacked@2026")
                .with(user(new UserDetailsImpl(secretary)))
                .with(csrf()));

        em.clear();
        assertThat(appUserRepository.findById(president.getId()).orElseThrow().getPasswordHash())
            .isEqualTo(hashBefore);
    }

    @Test
    void unSecretaireNeSAttribuePasLeRolePresident() {
        assertThatThrownBy(() -> policy.requireCanAssignRole(secretary.getId(), secretary.getId(), "PRESIDENT"))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("propre rôle");
        assertThatThrownBy(() -> policy.requireCanAssignRole(secretary.getId(), treasurer.getId(), "PRESIDENT"))
            .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> policy.requireCanCreate(secretary.getId(), "PRESIDENT"))
            .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void unSecretaireNeTouchePasAUnComptePresident() {
        assertThatThrownBy(() -> policy.requireCanAssignRole(secretary.getId(), president.getId(), "AUDITOR"))
            .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> policy.requireCanDeactivate(secretary.getId(), president.getId()))
            .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void lesRolesOrdinairesRestentAdministrablesParUnSecretaire() {
        assertThatCode(() -> policy.requireCanAssignRole(secretary.getId(), treasurer.getId(), "AUDITOR"))
            .doesNotThrowAnyException();
        assertThatCode(() -> policy.requireCanCreate(secretary.getId(), "CENSOR"))
            .doesNotThrowAnyException();
    }

    @Test
    void unPresidentPeutPromouvoirUnAutreMembre() {
        assertThatCode(() -> policy.requireCanAssignRole(president.getId(), secretary.getId(), "PRESIDENT"))
            .doesNotThrowAnyException();
    }

    @Test
    void leDernierPresidentActifNePeutPasEtreRetrograde() {
        assertThatThrownBy(() -> assignRoleUseCase.assignRole(president.getId(), "SECRETARY"))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("dernier compte PRESIDENT actif");

        // Avec un second PRESIDENT actif, la rétrogradation devient possible.
        assignRoleUseCase.assignRole(secretary.getId(), "PRESIDENT");
        assertThatCode(() -> assignRoleUseCase.assignRole(president.getId(), "SECRETARY"))
            .doesNotThrowAnyException();
    }

    // ── seed helpers ─────────────────────────────────────────

    private AppUser insertUser(String login, String roleName) {
        Long memberId = ((Number) em.createNativeQuery(
                "INSERT INTO member (full_name, join_date) VALUES (:n, CURRENT_DATE) RETURNING id")
            .setParameter("n", login)
            .getSingleResult()).longValue();
        em.createNativeQuery("""
                INSERT INTO app_user (login, password_hash, role_id, member_id, active)
                SELECT :login, 'x', r.id, :member, true FROM role r WHERE r.name = :role
                """)
            .setParameter("login", login)
            .setParameter("member", memberId)
            .setParameter("role", roleName)
            .executeUpdate();
        em.flush();
        return appUserRepository.findByLogin(login).orElseThrow();
    }
}
