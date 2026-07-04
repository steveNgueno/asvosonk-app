package org.asvosonk.security.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.domain.repository.AppUserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private static final int    MAX_ATTEMPTS   = 3;
    private static final int    LOCKOUT_MINUTES = 5;

    private final AppUserRepository appUserRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        AppUser user = appUserRepository.findByLogin(login)
            .orElseThrow(() -> new UsernameNotFoundException(
                "No account found for login: " + login));

        log.debug("Loading user '{}', active={}, locked={}", login, user.isActive(), user.isLocked());
        return new UserDetailsImpl(user);
    }

    /**
     * Called by the authentication success handler.
     * Resets failed-attempt counter and updates last login timestamp.
     */
    @Transactional
    public void handleLoginSuccess(String login) {
        appUserRepository.findByLogin(login).ifPresent(user -> {
            user.recordLoginSuccess();
            appUserRepository.save(user);
        });
    }

    /**
     * Called by the authentication failure handler.
     * Increments the failed-attempt counter; locks the account after MAX_ATTEMPTS.
     */
    @Transactional
    public void handleLoginFailure(String login) {
        appUserRepository.findByLogin(login).ifPresent(user -> {
            user.recordLoginFailure(MAX_ATTEMPTS, LOCKOUT_MINUTES);
            appUserRepository.save(user);
        });
    }
}
