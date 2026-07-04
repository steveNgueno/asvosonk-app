package org.asvosonk.common.domain.exception;

/**
 * Base runtime exception for all ASVOSONK domain-level errors.
 */
public class AsvosonkException extends RuntimeException {

    public AsvosonkException(String message) {
        super(message);
    }

    public AsvosonkException(String message, Throwable cause) {
        super(message, cause);
    }
}
