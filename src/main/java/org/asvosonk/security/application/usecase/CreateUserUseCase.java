package org.asvosonk.security.application.usecase;

import lombok.RequiredArgsConstructor;
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
        if (appUserRepository.existsByLogin(login)) {
            throw new IllegalArgumentException("Un utilisateur avec ce login existe déjà : " + login);
        }

        Role role = roleRepository.findByName(roleName)
            .orElseThrow(() -> new IllegalArgumentException("Rôle introuvable : " + roleName));

        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("Membre introuvable : " + memberId));

        String passwordHash = passwordEncoder.encode(rawPassword);
        LocalDateTime now = LocalDateTime.now();

        AppUser newUser = new AppUser(
            null, login, passwordHash, role, member,
            true, null, 0, null, now, now
        );

        return appUserRepository.save(newUser);
    }
}
