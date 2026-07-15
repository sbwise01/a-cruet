package com.bradandmarsha.acruet.auth;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Redirects unauthenticated browser traffic to Keycloak and enforces admin role on the admin WAR.
 */
public class OidcAuthFilter implements Filter {

    static final String STATE_SESSION_ATTRIBUTE = "acruet.oidc.state";

    private OidcSettings settings;
    private OidcService service;

    @Override
    public void init(jakarta.servlet.FilterConfig filterConfig) {
        settings = OidcSettings.fromEnvironment();
        if (!settings.isConfigured()) {
            return;
        }
        boolean requireAdmin = Boolean.parseBoolean(
                filterConfig.getInitParameter("requireAdminRole"));
        if (requireAdmin) {
            settings = new OidcSettings(
                    settings.clientId(),
                    settings.clientSecret(),
                    settings.issuer(),
                    settings.baseUrl(),
                    settings.adminRole(),
                    true);
        }
        service = new OidcService(settings);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (!settings.isConfigured() || isPublicPath(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = httpRequest.getSession(true);
        Object userObject = session.getAttribute(OidcUser.SESSION_ATTRIBUTE);
        if (!(userObject instanceof OidcUser user)) {
            redirectToLogin(httpRequest, httpResponse, session);
            return;
        }

        if (settings.requireAdminRole() && !user.hasRealmRole(settings.adminRole())) {
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResponse.setContentType("text/html; charset=UTF-8");
            httpResponse.getWriter().write(forbiddenPage(user));
            return;
        }

        chain.doFilter(request, response);
    }

    private void redirectToLogin(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpSession session) throws IOException {
        String state = OidcService.newState();
        session.setAttribute(STATE_SESSION_ATTRIBUTE, state);
        String loginUri = service.beginAuthorizationUri(state);
        response.sendRedirect(loginUri);
    }

    private boolean isPublicPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || path.isBlank()) {
            return false;
        }
        return path.equals("/health")
                || path.startsWith("/auth/")
                || path.equals("/signup")
                || path.startsWith("/signup/");
    }

    private static String forbiddenPage(OidcUser user) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>Forbidden</title>
                  <style>
                    body { font-family: system-ui, sans-serif; margin: 2rem; line-height: 1.5; }
                  </style>
                </head>
                <body>
                  <h1>403 Forbidden</h1>
                  <p>Signed in as <strong>%s</strong>, but this surface requires the administrator role.</p>
                  <p><a href="/auth/logout">Sign out</a></p>
                </body>
                </html>
                """.formatted(escape(user.displayName()));
    }

    private static String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
