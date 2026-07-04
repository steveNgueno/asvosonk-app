package org.asvosonk.security.infrastructure.persistence.mapper;

import org.asvosonk.member.domain.model.Member;
import org.asvosonk.member.infrastructure.persistence.entity.MemberEntity;
import org.asvosonk.member.infrastructure.persistence.mapper.MemberEntityMapper;
import org.asvosonk.security.domain.model.AppUser;
import org.asvosonk.security.domain.model.Permission;
import org.asvosonk.security.domain.model.Role;
import org.asvosonk.security.infrastructure.persistence.entity.AppUserEntity;
import org.asvosonk.security.infrastructure.persistence.entity.PermissionEntity;
import org.asvosonk.security.infrastructure.persistence.entity.RoleEntity;

import java.util.Set;
import java.util.stream.Collectors;

public class UserEntityMapper {

    // ── AppUser ──────────────────────────────────────────────

    public static AppUser toDomain(AppUserEntity entity) {
        if (entity == null) return null;
        Member member = MemberEntityMapper.toDomain(entity.getMember());
        Role role = toDomain(entity.getRole());

        return new AppUser(
            entity.getId(),
            entity.getLogin(),
            entity.getPasswordHash(),
            role,
            member,
            entity.isActive(),
            entity.getLastLogin(),
            entity.getFailedAttempts(),
            entity.getLockedUntil(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    public static AppUserEntity toEntity(AppUser domain) {
        if (domain == null) return null;
        AppUserEntity entity = new AppUserEntity();
        entity.setId(domain.getId());
        entity.setLogin(domain.getLogin());
        entity.setPasswordHash(domain.getPasswordHash());
        entity.setRole(toEntity(domain.getRole()));
        entity.setMember(MemberEntityMapper.toEntity(domain.getMember()));
        entity.setActive(domain.isActive());
        entity.setLastLogin(domain.getLastLogin());
        entity.setFailedAttempts(domain.getFailedAttempts());
        entity.setLockedUntil(domain.getLockedUntil());
        return entity;
    }

    // ── Role ─────────────────────────────────────────────────

    public static Role toDomain(RoleEntity entity) {
        if (entity == null) return null;
        Set<Permission> permissions = entity.getPermissions().stream()
            .map(UserEntityMapper::toDomain)
            .collect(Collectors.toSet());
        return new Role(
            entity.getId(),
            entity.getName(),
            entity.getDescription(),
            permissions
        );
    }

    public static RoleEntity toEntity(Role domain) {
        if (domain == null) return null;
        RoleEntity entity = new RoleEntity();
        entity.setId(domain.getId());
        entity.setName(domain.getName());
        entity.setDescription(domain.getDescription());
        entity.setPermissions(domain.getPermissions().stream()
            .map(UserEntityMapper::toEntity)
            .collect(Collectors.toSet()));
        return entity;
    }

    // ── Permission ───────────────────────────────────────────

    public static Permission toDomain(PermissionEntity entity) {
        if (entity == null) return null;
        return new Permission(
            entity.getId(),
            entity.getCode(),
            entity.getDescription()
        );
    }

    public static PermissionEntity toEntity(Permission domain) {
        if (domain == null) return null;
        PermissionEntity entity = new PermissionEntity();
        entity.setId(domain.getId());
        entity.setCode(domain.getCode());
        entity.setDescription(domain.getDescription());
        return entity;
    }
}
