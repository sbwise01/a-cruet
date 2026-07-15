package com.bradandmarsha.acruet.user.rest;

import com.bradandmarsha.acruet.auth.OidcUser;
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
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>a-cruet</title>
                  <style>
                    body { font-family: system-ui, sans-serif; margin: 2rem; line-height: 1.5; }
                    h1 { margin-bottom: 0.25rem; }
                    p { color: #444; max-width: 40rem; }
                    a { color: #1d4ed8; }
                    .actions { margin-top: 1.5rem; }
                    .actions a { margin-right: 1rem; }
                  </style>
                </head>
                <body>
                  <h1>a-cruet</h1>
                  <p>Envelope budgeting for intentional savings — allocate money across goals and track balances over time.</p>
                  <p>Access is by application and admin approval. Existing users can sign in with Keycloak.</p>
                  <p class="actions">
                    <a href="/signup">Apply for access</a>
                    <a href="/auth/login">Sign in</a>
                  </p>
                </body>
                </html>
                """;
    }

    private String authenticatedPage(OidcUser user) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>a-cruet</title>
                  <style>
                    body { font-family: system-ui, sans-serif; margin: 2rem; line-height: 1.5; }
                    h1 { margin-bottom: 0.25rem; }
                    p { color: #444; max-width: 40rem; }
                    a { color: #1d4ed8; }
                  </style>
                </head>
                <body>
                  <h1>a-cruet</h1>
                  <p>Signed in as <strong>%s</strong>.</p>
                  <p>Envelope budgeting for intentional savings. Ledger features arrive in later rollout phases.</p>
                  <p><a href="/auth/logout">Sign out</a></p>
                </body>
                </html>
                """.formatted(escape(user.displayName()));
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
