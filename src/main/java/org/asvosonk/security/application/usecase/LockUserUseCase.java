package org.asvosonk.security.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.domain.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LockUserUseCase {

    private final AppUserRepository appUserRepository;

    @Transactional
    public AppUser lockUser(Long userId, int lockoutMinutes) {
        AppUser user = appUserRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + userId));

        user.lockUntil(LocalDateTime.now().plusMinutes(lockoutMinutes));
        return appUserRepository.save(user);
    }

    @Transactional
    public AppUser unlockUser(Long userId) {
        AppUser user = appUserRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + userId));

        user.unlock();
        return appUserRepository.save(user);
    }

    @Transactional
    public AppUser deactivateUser(Long userId) {
        AppUser user = appUserRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + userId));

        user.deactivate();
        return appUserRepository.save(user);
    }

    @Transactional
    public AppUser activateUser(Long userId) {
        AppUser user = appUserRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + userId));

        user.activate();
        return appUserRepository.save(user);
    }
}
