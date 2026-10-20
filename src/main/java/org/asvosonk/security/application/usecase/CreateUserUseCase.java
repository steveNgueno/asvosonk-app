package org.asvosonk.security.application.usecase;

import lombok.RequiredArgsConstructor;
import org.asvosonk.common.domain.exception.BusinessRuleException;
import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.domain.repository.MemberRepository;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.domain.model.Role;
import org.asvosonk.security.domain.repository.AppUserRepository;
import org.asvosonk.security.domain.repository.RoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CreateUserUseCase {

    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AppUser createUser(String login, String rawPassword, String roleName, Long memberId) {
        login = login == null ? null : login.trim();
        // Comparaison insensible à la casse : « Tresorier » existe déjà si « tresorier » existe.
        if (appUserRepository.existsByLogin(login)) {
            throw new IllegalArgumentException("Un utilisateur avec ce login existe déjà : " + login);
        }

        Role role = roleRepository.findByName(roleName)
            .orElseThrow(() -> new IllegalArgumentException("Rôle introuvable : " + roleName));

        if (memberId == null) {
            throw new BusinessRuleException("Sélectionnez le membre auquel rattacher ce compte.");
        }
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("Membre introuvable : " + memberId));
        // Un compte ne se crée que pour un membre actif (un membre suspendu,
        // démissionnaire ou décédé n'a pas à accéder à l'application).
        if (!member.isActive()) {
            throw new BusinessRuleException(
                "Compte refusé : " + member.getFullName() + " n'est pas un membre actif ("
              + member.getStatusLabel() + ").");
        }
        if (appUserRepository.findAll().stream()
                .anyMatch(u -> u.getMember() != null && memberId.equals(u.getMember().getId()))) {
            throw new BusinessRuleException(
                member.getFullName() + " possède déjà un compte utilisateur.");
        }

        String passwordHash = passwordEncoder.encode(rawPassword);
        LocalDateTime now = LocalDateTime.now();

        AppUser newUser = new AppUser(
            null, login, passwordHash, role, member,
            true, null, 0, null, now, now
        );

        return appUserRepository.save(newUser);
    }
}
