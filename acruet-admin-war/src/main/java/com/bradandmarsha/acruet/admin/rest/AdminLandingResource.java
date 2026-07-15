package com.bradandmarsha.acruet.admin.rest;

import com.bradandmarsha.acruet.approval.ApprovalService;
import com.bradandmarsha.acruet.auth.OidcUser;
import com.bradandmarsha.acruet.ui.AdminPageLayout;
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

    private final ApprovalService approvalService = ApprovalService.fromEnvironment();

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String index(@Context HttpServletRequest request) {
        String displayName = currentUser(request)
                .map(OidcUser::displayName)
                .orElse("administrator");
        int pendingCount = approvalService.listPending().size();
        String queueLink = pendingCount == 0
                ? "<p class=\"hint\">No applications are waiting for approval.</p>"
                : "<p><a href=\"/approvals\">Review "
                        + pendingCount
                        + " pending application"
                        + (pendingCount == 1 ? "" : "s")
                        + "</a></p>";
        return AdminPageLayout.render(
                AdminPageLayout.APP_NAME,
                """
                <p>Signed in as <strong>%s</strong> with administrator access.</p>
                %s
                <p class="hint">User management, suspension, and abuse monitoring arrive in later rollout phases.</p>
                <p><a href="/auth/logout">Sign out</a></p>
                """
                        .formatted(escape(displayName), queueLink));
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
