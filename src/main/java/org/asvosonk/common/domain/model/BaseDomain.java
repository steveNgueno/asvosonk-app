package org.asvosonk.common.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Base class for domain models with common timestamp fields.
 * Domain models that need createdAt/updatedAt can extend this.
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
public abstract class BaseDomain {

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
