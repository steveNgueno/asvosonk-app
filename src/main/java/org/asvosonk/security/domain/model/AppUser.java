package org.asvosonk.security.domain.model;

import lombok.*;
import org.asvosonk.member.domain.model.Member;

import java.time.LocalDateTime;

/**
 * Pure domain model for an application user (authentication identity).
 * All JPA concerns are handled by AppUserEntity in the infrastructure layer.
 */
@Getter
@EqualsAndHashCode(of = "id")
public class AppUser {

    private final Long id;
    private String login;
    private String passwordHash;
    private Role role;
    private final Member member;
    private boolean active;
    private LocalDateTime lastLogin;
    private int failedAttempts;
    private LocalDateTime lockedUntil;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AppUser(Long id, String login, String passwordHash, Role role, Member member,
                   boolean active, LocalDateTime lastLogin, int failedAttempts,
                   LocalDateTime lockedUntil, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.login = login;
        this.passwordHash = passwordHash;
        this.role = role;
        this.member = member;
        this.active = active;
        this.lastLogin = lastLogin;
        this.failedAttempts = failedAttempts;
        this.lockedUntil = lockedUntil;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    public boolean hasPermission(String permissionCode) {
        return role != null && role.hasPermission(permissionCode);
    }

    public void recordLoginSuccess() {
        this.failedAttempts = 0;
        this.lockedUntil = null;
        this.lastLogin = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void recordLoginFailure(int maxAttempts, int lockoutMinutes) {
        this.failedAttempts++;
        if (this.failedAttempts >= maxAttempts) {
            this.lockedUntil = LocalDateTime.now().plusMinutes(lockoutMinutes);
        }
        this.updatedAt = LocalDateTime.now();
    }

    public void assignRole(Role newRole) {
        this.role = newRole;
        this.updatedAt = LocalDateTime.now();
    }

    public void updatePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.updatedAt = LocalDateTime.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = LocalDateTime.now();
    }

    public void activate() {
        this.active = true;
        this.updatedAt = LocalDateTime.now();
    }

    public void lockUntil(LocalDateTime until) {
        this.lockedUntil = until;
        this.updatedAt = LocalDateTime.now();
    }

    public void unlock() {
        this.lockedUntil = null;
        this.failedAttempts = 0;
        this.updatedAt = LocalDateTime.now();
    }
}
