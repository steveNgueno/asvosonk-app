package org.asvosonk.security.domain.model;

import lombok.*;

import java.util.Objects;

@Getter
@EqualsAndHashCode(of = "id")
public class Permission {

    private final Integer id;
    private final String code;
    private final String description;

    public Permission(Integer id, String code, String description) {
        this.id = id;
        this.code = Objects.requireNonNull(code, "Permission code must not be null");
        this.description = description;
    }
}
