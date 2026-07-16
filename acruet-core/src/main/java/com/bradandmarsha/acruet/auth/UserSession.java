package com.bradandmarsha.acruet.auth;

import com.bradandmarsha.acruet.keys.KeyService;
import com.bradandmarsha.acruet.user.AcruetUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.time.Instant;
import java.util.Optional;

/**
 * Resolves OIDC session user to provisioned {@link AcruetUser} rows.
 */
public final class UserSession {

    public static final String KEY_SETUP_COMPLETE_ATTRIBUTE = "acruet.key_setup_complete";

    private static final KeyService KEY_SERVICE = new KeyService();

    private UserSession() {
    }

    public static Optional<OidcUser> oidcUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }
        Object userObject = session.getAttribute(OidcUser.SESSION_ATTRIBUTE);
        if (userObject instanceof OidcUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static Optional<AcruetUser> acruetUser(HttpServletRequest request) {
        return oidcUser(request).flatMap(user -> KEY_SERVICE.findUser(user.subject()));
    }

    public static void refreshKeySetupFlag(HttpServletRequest request, AcruetUser user) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(KEY_SETUP_COMPLETE_ATTRIBUTE, user.keySetupComplete());
        }
    }

    public static boolean isKeySetupComplete(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        Object flag = session.getAttribute(KEY_SETUP_COMPLETE_ATTRIBUTE);
        return flag instanceof Boolean complete && complete;
    }

    public static void onLogin(HttpServletRequest request, OidcUser oidcUser) {
        KEY_SERVICE.findUser(oidcUser.subject()).ifPresent(user -> {
            KEY_SERVICE.recordLogin(user, Instant.now());
            refreshKeySetupFlag(request, user);
        });
    }

    public static void markKeySetupComplete(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(KEY_SETUP_COMPLETE_ATTRIBUTE, true);
        }
    }
}
