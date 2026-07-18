package com.bradandmarsha.acruet.ui;

import com.bradandmarsha.acruet.auth.OidcUser;
import com.bradandmarsha.acruet.user.AcruetUser;

/**
 * Two-letter avatar initials for authenticated user chrome (Phase 9).
 */
public final class UserInitials {

    private UserInitials() {
    }

    public static String from(AcruetUser acruetUser, OidcUser oidcUser) {
        return fromDisplayName(acruetUser.displayName(), fallbackEmail(acruetUser, oidcUser));
    }

    public static String from(OidcUser oidcUser) {
        String fromNames = fromGivenFamily(oidcUser.givenName(), oidcUser.familyName());
        if (!fromNames.isBlank()) {
            return fromNames;
        }
        return fromEmail(fallbackEmail(null, oidcUser));
    }

    public static String displayName(AcruetUser acruetUser, OidcUser oidcUser) {
        if (acruetUser != null && !acruetUser.displayName().isBlank()) {
            return acruetUser.displayName();
        }
        String fromNames = combinedName(oidcUser.givenName(), oidcUser.familyName());
        if (!fromNames.isBlank()) {
            return fromNames;
        }
        return oidcUser.displayName();
    }

    private static String fromDisplayName(String displayName, String email) {
        if (displayName == null || displayName.isBlank()) {
            return fromEmail(email);
        }
        String[] parts = displayName.trim().split("\\s+");
        if (parts.length >= 2) {
            return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
        }
        if (parts.length == 1 && parts[0].length() >= 2) {
            return parts[0].substring(0, 2).toUpperCase();
        }
        return fromEmail(email);
    }

    private static String fromGivenFamily(String givenName, String familyName) {
        if (givenName == null || givenName.isBlank() || familyName == null || familyName.isBlank()) {
            return "";
        }
        return ("" + givenName.trim().charAt(0) + familyName.trim().charAt(0)).toUpperCase();
    }

    private static String combinedName(String givenName, String familyName) {
        if (givenName == null || givenName.isBlank()) {
            return familyName == null ? "" : familyName.trim();
        }
        if (familyName == null || familyName.isBlank()) {
            return givenName.trim();
        }
        return givenName.trim() + " " + familyName.trim();
    }

    private static String fromEmail(String email) {
        if (email == null || email.isBlank()) {
            return "??";
        }
        String local = email.split("@")[0];
        if (local.length() >= 2) {
            return local.substring(0, 2).toUpperCase();
        }
        return local.toUpperCase();
    }

    private static String fallbackEmail(AcruetUser acruetUser, OidcUser oidcUser) {
        if (acruetUser != null && acruetUser.email() != null && !acruetUser.email().isBlank()) {
            return acruetUser.email();
        }
        return oidcUser.email();
    }
}
