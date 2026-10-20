package org.asvosonk.security.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.security.application.service.UserAdministrationPolicy;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.domain.model.Role;
import org.asvosonk.security.domain.repository.AppUserRepository;
import org.asvosonk.security.domain.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AssignRoleUseCase {

    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;
    private final UserAdministrationPolicy userAdministrationPolicy;

    @Transactional
    public void assignRole(Long userId, String roleName) {
        AppUser user = appUserRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + userId));

        Role role = roleRepository.findByName(roleName)
            .orElseThrow(() -> new IllegalArgumentException("Rôle introuvable : " + roleName));

        // Rétrograder le dernier PRESIDENT actif laisserait l'association sans
        // administrateur (la désactivation était protégée, pas le changement de rôle).
        if (UserAdministrationPolicy.isPresident(user) && user.isActive()
                && !UserAdministrationPolicy.PRESIDENT.equals(role.getName())) {
            userAdministrationPolicy.requireAnotherActivePresident(user);
        }

        user.assignRole(role);
        appUserRepository.save(user);
    }
}
