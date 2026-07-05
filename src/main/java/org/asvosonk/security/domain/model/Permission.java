package org.asvosonk.security.domain.model;

import java.util.Objects;

public record Permission(Integer id, String code, String description) {

    public Permission(Integer id, String code, String description) {
        this.id = id;
        this.code = Objects.requireNonNull(code, "Permission code must not be null");
        this.description = description;
    }
}
