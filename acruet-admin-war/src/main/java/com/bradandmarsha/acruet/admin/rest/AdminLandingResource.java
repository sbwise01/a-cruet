package com.bradandmarsha.acruet.admin.rest;

import com.bradandmarsha.acruet.auth.OidcUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

/**
 * Authenticated administrator landing page (Phase 4).
 */
@Path("/")
public class AdminLandingResource {

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String index(@Context HttpServletRequest request) {
        String displayName = currentUser(request)
                .map(OidcUser::displayName)
                .orElse("administrator");
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>a-cruet Administration</title>
                  <style>
                    body { font-family: system-ui, sans-serif; margin: 2rem; line-height: 1.5; }
                    h1 { margin-bottom: 0.25rem; }
                    p { color: #444; max-width: 40rem; }
                    a { color: #1d4ed8; }
                  </style>
                </head>
                <body>
                  <h1>a-cruet Administration</h1>
                  <p>Signed in as <strong>%s</strong> with administrator access.</p>
                  <p>Approval queue, user management, and abuse monitoring arrive in later rollout phases.</p>
                  <p><a href="/auth/logout">Sign out</a></p>
                </body>
                </html>
                """.formatted(escape(displayName));
    }

    private static java.util.Optional<OidcUser> currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return java.util.Optional.empty();
        }
        Object userObject = session.getAttribute(OidcUser.SESSION_ATTRIBUTE);
        if (userObject instanceof OidcUser user) {
            return java.util.Optional.of(user);
        }
        return java.util.Optional.empty();
    }

    private static String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
