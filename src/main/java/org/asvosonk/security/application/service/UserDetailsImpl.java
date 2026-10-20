package org.asvosonk.security.application.service;

import lombok.Getter;
import org.asvosonk.member.domain.valueobject.MemberStatus;
import org.asvosonk.security.domain.model.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Adapts AppUser to Spring Security's UserDetails contract.
 * Permissions are mapped as GrantedAuthorities so that
 * @PreAuthorize("hasAuthority('SESSION_CLOSE')") works out of the box.
 */
public class UserDetailsImpl implements UserDetails {

    @Getter
    private final AppUser appUser;

    private final Collection<GrantedAuthority> authorities;

    public UserDetailsImpl(AppUser appUser) {
        this.appUser = appUser;
        // Map every permission code to a GrantedAuthority
        this.authorities = appUser.getRole()
                                  .getPermissions()
                                  .stream()
                                  .map(p -> new SimpleGrantedAuthority(p.code()))
                                  .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return appUser.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return appUser.getLogin();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !appUser.isLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Compte désactivé, ou rattaché à un membre qui a quitté l'association
     * (démissionnaire ou décédé) : la connexion est refusée. Un membre suspendu
     * garde son accès, la suspension étant temporaire.
     */
    @Override
    public boolean isEnabled() {
        if (!appUser.isActive()) {
            return false;
        }
        MemberStatus memberStatus = appUser.getMember() != null ? appUser.getMember().getStatus() : null;
        return memberStatus != MemberStatus.resigned && memberStatus != MemberStatus.deceased;
    }

    /**
     * Égalité sur le login (insensible à la casse, comme la connexion).
     *
     * <p>Indispensable à la limite d'une session par utilisateur
     * ({@code maximumSessions(1)}) : le registre des sessions indexe les sessions
     * par principal. Sans {@code equals}/{@code hashCode}, chaque connexion
     * produisait un principal « différent » et la limite n'était jamais
     * appliquée.</p>
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof UserDetailsImpl that && loginKey().equals(that.loginKey());
    }

    @Override
    public int hashCode() {
        return loginKey().hashCode();
    }

    private String loginKey() {
        return getUsername() == null ? "" : getUsername().trim().toLowerCase(Locale.ROOT);
    }
}
