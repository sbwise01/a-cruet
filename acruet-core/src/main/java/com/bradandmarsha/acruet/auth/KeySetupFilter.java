package com.bradandmarsha.acruet.auth;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Redirects authenticated users without completed key setup to {@code /keys/setup} (Phase 7).
 */
public class KeySetupFilter implements Filter {

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

        if (!UserSession.isKeySetupComplete(httpRequest)) {
            httpResponse.sendRedirect(httpRequest.getContextPath() + "/keys/setup");
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
