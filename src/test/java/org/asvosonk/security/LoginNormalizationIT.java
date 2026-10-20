package org.asvosonk.security;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.valueobject.MemberStatus;
import org.asvosonk.security.application.service.UserDetailsImpl;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.domain.repository.AppUserRepository;
import org.asvosonk.security.presentation.request.UserForm;
import org.asvosonk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Login : espaces de bord ignorés, casse ignorée, caractères restreints ; une
 * seule session par utilisateur (égalité des principaux sur le login) ; pas de
 * connexion pour le compte d'un membre démissionnaire ou décédé.
 */
@SpringBootTest
@Transactional
class LoginNormalizationIT extends AbstractIntegrationTest {

    @Autowired AppUserRepository appUserRepository;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void leLoginSeRetrouveSansTenirCompteDesEspacesNiDeLaCasse() {
        assertThat(appUserRepository.findByLogin("  ADMIN ")).isPresent();
        assertThat(appUserRepository.existsByLogin("Admin")).isTrue();
    }

    @Test
    void leFormulaireNettoieEtRestreintLeLogin() {
        UserForm form = new UserForm();
        form.setLogin("  Tresorier ");
        assertThat(form.getLogin()).isEqualTo("Tresorier");
        assertThat(validator.validateProperty(form, "login")).isEmpty();

        form.setLogin("jean dupont");
        assertThat(validator.validateProperty(form, "login")).isNotEmpty();
        form.setLogin("ab");
        assertThat(validator.validateProperty(form, "login")).isNotEmpty();
    }

    @Test
    void deuxConnexionsDuMemeUtilisateurDesignentLeMemePrincipal() {
        AppUser admin = appUserRepository.findByLogin("admin").orElseThrow();
        AppUser sameLoginOtherCase = withLoginAndMember(admin, "ADMIN", admin.getMember());

        assertThat(new UserDetailsImpl(admin)).isEqualTo(new UserDetailsImpl(sameLoginOtherCase));
        assertThat(new UserDetailsImpl(admin).hashCode())
            .isEqualTo(new UserDetailsImpl(sameLoginOtherCase).hashCode());
    }

    @Test
    void unMembreDecedeOuDemissionnaireNeSeConnectePlus() {
        AppUser admin = appUserRepository.findByLogin("admin").orElseThrow();

        assertThat(new UserDetailsImpl(withLoginAndMember(admin, "x", member(MemberStatus.deceased))).isEnabled()).isFalse();
        assertThat(new UserDetailsImpl(withLoginAndMember(admin, "x", member(MemberStatus.resigned))).isEnabled()).isFalse();
        // La suspension est temporaire : l'accès est conservé.
        assertThat(new UserDetailsImpl(withLoginAndMember(admin, "x", member(MemberStatus.suspended))).isEnabled()).isTrue();
    }

    private static Member member(MemberStatus status) {
        return new Member(99L, "Membre", null, LocalDate.now(), true, status, null, null);
    }

    private static AppUser withLoginAndMember(AppUser source, String login, Member member) {
        return new AppUser(source.getId(), login, source.getPasswordHash(), source.getRole(), member,
            true, null, 0, null, null, null);
    }
}
