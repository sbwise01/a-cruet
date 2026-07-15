package com.bradandmarsha.acruet.user.rest;

import com.bradandmarsha.acruet.auth.OidcUser;
import com.bradandmarsha.acruet.ui.UserPageLayout;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import java.util.Optional;

/**
 * User landing page — public welcome for anonymous visitors; app home when signed in.
 */
@Path("/")
public class LandingResource {

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String index(@Context HttpServletRequest request) {
        Optional<OidcUser> user = currentUser(request);
        return user.map(this::authenticatedPage).orElseGet(this::publicPage);
    }

    private String publicPage() {
        return UserPageLayout.render(
                UserPageLayout.APP_NAME,
                """
                <p>Allocate money across savings goals and track balances over time.</p>
                <p class="hint">Access is by application and admin approval. Existing users can sign in with Keycloak.</p>
                <p class="actions">
                  <a href="/signup">Apply for access</a>
                  <a href="/auth/login">Sign in</a>
                </p>
                """);
    }

    private String authenticatedPage(OidcUser user) {
        return UserPageLayout.render(
                UserPageLayout.APP_NAME,
                """
                <p>Signed in as <strong>%s</strong>.</p>
                <p>Ledger features arrive in later rollout phases.</p>
                <p><a href="/auth/logout">Sign out</a></p>
                """.formatted(escape(user.displayName())));
    }

    private static Optional<OidcUser> currentUser(HttpServletRequest request) {
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

    private static String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
