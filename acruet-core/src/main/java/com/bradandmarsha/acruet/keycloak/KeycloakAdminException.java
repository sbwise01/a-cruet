package com.bradandmarsha.acruet.keycloak;

/**
 * Raised when Keycloak Admin API calls fail.
 */
public final class KeycloakAdminException extends RuntimeException {

    public KeycloakAdminException(String message) {
        super(message);
    }

    public KeycloakAdminException(String message, Throwable cause) {
        super(message, cause);
    }
}
