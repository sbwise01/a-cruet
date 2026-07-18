package com.bradandmarsha.acruet.auth;

import com.bradandmarsha.acruet.keys.KeyService;
import com.bradandmarsha.acruet.user.AcruetUser;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Optional;

/**
 * Redirects authenticated users without completed key setup or recovery enrollment (Phase 7).
 */
public class KeySetupFilter implements Filter {

    private static final KeyService KEY_SERVICE = new KeyService();

    private boolean enforceKeySetup;

    @Override
    public void init(jakarta.servlet.FilterConfig filterConfig) {
        enforceKeySetup = Boolean.parseBoolean(filterConfig.getInitParameter("enforceKeySetup"));
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!enforceKeySetup) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (isExemptPath(httpRequest) || UserSession.oidcUser(httpRequest).isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        Optional<AcruetUser> acruetUser = UserSession.acruetUser(httpRequest);
        if (acruetUser.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        if (!UserSession.isKeySetupComplete(httpRequest)) {
            httpResponse.sendRedirect(httpRequest.getContextPath() + "/keys/setup");
            return;
        }

        if (!KEY_SERVICE.status(acruetUser.get()).recoveryEnrolled()) {
            httpResponse.sendRedirect(httpRequest.getContextPath() + "/keys/enroll-recovery");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isExemptPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || path.isBlank()) {
            return false;
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (path.isEmpty()) {
            path = "/";
        }
        return path.equals("/health")
                || path.startsWith("/auth/")
                || path.equals("/signup")
                || path.startsWith("/signup/")
                || path.startsWith("/keys/")
                || path.startsWith("/static/");
    }
}
