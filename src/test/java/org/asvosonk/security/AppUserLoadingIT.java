package org.asvosonk.security;

import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.domain.repository.AppUserRepository;
import org.asvosonk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression: loading a user outside a transaction blew up with
 * <em>"could not initialize proxy … no Session"</em>, because the linked member
 * is a LAZY association and {@code open-in-view} is disabled. The user edit
 * screen answered HTTP 500 and the deactivate action failed with a technical
 * message.
 *
 * <p>The test deliberately runs <strong>without</strong> {@code @Transactional},
 * which is exactly the situation of a controller reading the repository: the
 * association must already be fetched by the query.
 */
@SpringBootTest
class AppUserLoadingIT extends AbstractIntegrationTest {

    @Autowired AppUserRepository appUserRepository;

    @Test
    void findByIdExposesMemberAndRoleOutsideAnyTransaction() {
        Optional<AppUser> admin = appUserRepository.findByLogin("admin");
        assertThat(admin).isPresent();

        AppUser reloaded = appUserRepository.findById(admin.get().getId()).orElseThrow();

        // Touching these outside a session used to throw LazyInitializationException.
        assertThat(reloaded.getMember()).isNotNull();
        assertThat(reloaded.getMember().getFullName()).isNotBlank();
        assertThat(reloaded.getRole().getName()).isEqualTo("PRESIDENT");
        assertThat(reloaded.getRole().getPermissions()).isNotEmpty();
    }
}
