package org.asvosonk.security.application.service;

import lombok.Getter;
import org.asvosonk.security.domain.model.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
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

    @Override
    public boolean isEnabled() {
        return appUser.isActive();
    }
}
