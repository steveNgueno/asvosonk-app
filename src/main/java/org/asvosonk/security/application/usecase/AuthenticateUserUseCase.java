package org.asvosonk.security.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.domain.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthenticateUserUseCase {

    private static final int MAX_ATTEMPTS = 3;
    private static final int LOCKOUT_MINUTES = 5;

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Validates credentials and handles login success/failure tracking.
     * @return the AppUser domain model if authentication succeeds, null otherwise
     */
    @Transactional
    public AppUser authenticate(String login, String rawPassword) {
        return appUserRepository.findByLogin(login)
            .map(user -> {
                if (user.isLocked()) return null;
                if (passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
                    user.recordLoginSuccess();
                    appUserRepository.save(user);
                    return user;
                } else {
                    user.recordLoginFailure(MAX_ATTEMPTS, LOCKOUT_MINUTES);
                    appUserRepository.save(user);
                    return null;
                }
            })
            .orElse(null);
    }

    @Transactional
    public void handleLoginSuccess(String login) {
        appUserRepository.findByLogin(login).ifPresent(user -> {
            user.recordLoginSuccess();
            appUserRepository.save(user);
        });
    }

    @Transactional
    public void handleLoginFailure(String login) {
        appUserRepository.findByLogin(login).ifPresent(user -> {
            user.recordLoginFailure(MAX_ATTEMPTS, LOCKOUT_MINUTES);
            appUserRepository.save(user);
        });
    }
}
