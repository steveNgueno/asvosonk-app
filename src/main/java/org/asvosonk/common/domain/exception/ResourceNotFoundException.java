package org.asvosonk.common.domain.exception;

/**
 * Thrown when a requested domain entity is not found.
 */
public class ResourceNotFoundException extends AsvosonkException {

    private final String resourceName;
    private final Object resourceId;

    public ResourceNotFoundException(String resourceName, Object resourceId) {
        super(resourceName + " introuvable : " + resourceId);
        this.resourceName = resourceName;
        this.resourceId = resourceId;
    }

    public ResourceNotFoundException(String message) {
        super(message);
        this.resourceName = null;
        this.resourceId = null;
    }

    public String getResourceName() { return resourceName; }
    public Object getResourceId()   { return resourceId; }
}
