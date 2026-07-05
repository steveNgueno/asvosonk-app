package org.asvosonk.security.domain.model;

import lombok.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Getter
@EqualsAndHashCode(of = "id")
public class Role {

    private final Integer id;
    private final String name;
    private final String description;
    private final Set<Permission> permissions;

    public Role(Integer id, String name, String description, Set<Permission> permissions) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "Role name must not be null");
        this.description = description;
        this.permissions = permissions != null
            ? Collections.unmodifiableSet(new HashSet<>(permissions))
            : Collections.emptySet();
    }

    public boolean hasPermission(String permissionCode) {
        return permissions.stream()
            .anyMatch(p -> p.code().equals(permissionCode));
    }
}
