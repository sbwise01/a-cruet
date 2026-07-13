package com.bradandmarsha.acruet.auth;

import java.util.Objects;
import java.util.Set;

/**
 * Authenticated user attributes stored in the HTTP session after OIDC callback.
 */
public final class OidcUser {

    public static final String SESSION_ATTRIBUTE = "acruet.oidc.user";

    private final String subject;
    private final String preferredUsername;
    private final String email;
    private final Set<String> realmRoles;

    public OidcUser(String subject, String preferredUsername, String email, Set<String> realmRoles) {
        this.subject = Objects.requireNonNull(subject, "subject");
        this.preferredUsername = preferredUsername;
        this.email = email;
        this.realmRoles = Set.copyOf(realmRoles);
    }

    public String subject() {
        return subject;
    }

    public String preferredUsername() {
        return preferredUsername;
    }

    public String email() {
        return email;
    }

    public Set<String> realmRoles() {
        return realmRoles;
    }

    public boolean hasRealmRole(String role) {
        return realmRoles.contains(role);
    }

    public String displayName() {
        if (preferredUsername != null && !preferredUsername.isBlank()) {
            return preferredUsername;
        }
        if (email != null && !email.isBlank()) {
            return email;
        }
        return subject;
    }
}
