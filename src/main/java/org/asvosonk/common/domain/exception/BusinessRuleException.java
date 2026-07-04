package org.asvosonk.common.domain.exception;

/**
 * Thrown when a business rule is violated.
 * Carries a user-facing message in French and an optional error code.
 */
public class BusinessRuleException extends AsvosonkException {

    private final String errorCode;

    public BusinessRuleException(String message) {
        super(message);
        this.errorCode = null;
    }

    public BusinessRuleException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }
}
