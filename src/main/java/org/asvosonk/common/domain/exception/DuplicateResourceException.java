package org.asvosonk.common.domain.exception;

/**
 * Thrown when attempting to create a resource that already exists.
 */
public class DuplicateResourceException extends AsvosonkException {

    public DuplicateResourceException(String message) {
        super(message);
    }

    public DuplicateResourceException(String resourceName, String field, Object value) {
        super("Un " + resourceName + " avec " + field + " « " + value + " » existe déjà.");
    }
}
