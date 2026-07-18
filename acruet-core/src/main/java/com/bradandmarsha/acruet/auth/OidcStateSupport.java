package com.bradandmarsha.acruet.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Persists OIDC CSRF state in the HTTP session and a short-lived cookie so callbacks
 * succeed when the user app runs multiple replicas (Phase 9 item 10 verify path).
 */
final class OidcStateSupport {

    static final String COOKIE_NAME = "acruet_oidc_state";
    private static final int MAX_AGE_SECONDS = 600;

    private OidcStateSupport() {
    }

    static void save(HttpServletRequest request, HttpServletResponse response, HttpSession session, String state) {
        session.setAttribute(OidcAuthFilter.STATE_SESSION_ATTRIBUTE, state);
        response.addCookie(stateCookie(request, state, MAX_AGE_SECONDS));
    }

    static boolean matches(HttpServletRequest request, HttpSession session, String state) {
        if (state == null || state.isBlank()) {
            return false;
        }
        Object expectedState = session.getAttribute(OidcAuthFilter.STATE_SESSION_ATTRIBUTE);
        if (expectedState != null && state.equals(expectedState.toString())) {
            return true;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName()) && state.equals(cookie.getValue())) {
                return true;
            }
        }
        return false;
    }

    static void clear(HttpServletRequest request, HttpServletResponse response, HttpSession session) {
        session.removeAttribute(OidcAuthFilter.STATE_SESSION_ATTRIBUTE);
        response.addCookie(stateCookie(request, "", 0));
    }

    private static Cookie stateCookie(HttpServletRequest request, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(COOKIE_NAME, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        return cookie;
    }
}
