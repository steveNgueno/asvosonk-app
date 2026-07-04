package org.asvosonk.security.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.domain.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdatePasswordUseCase {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AppUser updatePassword(Long userId, String newRawPassword) {
        AppUser user = appUserRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + userId));

        user.updatePassword(passwordEncoder.encode(newRawPassword));
        return appUserRepository.save(user);
    }

    @Transactional
    public AppUser updatePassword(String login, String newRawPassword) {
        AppUser user = appUserRepository.findByLogin(login)
            .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + login));

        user.updatePassword(passwordEncoder.encode(newRawPassword));
        return appUserRepository.save(user);
    }
}
