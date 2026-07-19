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
    private final com.bradandmarsha.acruet.admin.AdminOpsService adminOpsService =
            com.bradandmarsha.acruet.admin.AdminOpsService.fromEnvironment();

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String index(@Context HttpServletRequest request) {
        String displayName = currentUser(request)
                .map(OidcUser::displayName)
                .orElse("administrator");
        int pendingCount = approvalService.listPending().size();
        int anomalyCount = adminOpsService.listRecentAnomalies(100).stream()
                .filter(anomaly -> anomaly.alertedAt() == null)
                .toList()
                .size();
        String queueLink = pendingCount == 0
                ? ""
                : "<p><a href=\"/approvals\">Review "
                        + pendingCount
                        + " pending application"
                        + (pendingCount == 1 ? "" : "s")
                        + "</a></p>";
        String anomalyLink = anomalyCount == 0
                ? ""
                : "<p><a href=\"/anomalies\">"
                        + anomalyCount
                        + " unalerted login anomal"
                        + (anomalyCount == 1 ? "y" : "ies")
                        + "</a></p>";
        return AdminPageLayout.render(
                AdminPageLayout.APP_NAME,
                """
                <p>Signed in as <strong>%s</strong> with administrator access.</p>
                %s
                %s
                <ul>
                  <li><a href="/approvals">Approval queue</a></li>
                  <li><a href="/users">User list</a></li>
                  <li><a href="/blocked-signups">Blocked signups</a></li>
                  <li><a href="/anomalies">Login anomalies</a></li>
                </ul>
                <p><a href="/auth/logout">Sign out</a></p>
                """
                        .formatted(escape(displayName), queueLink, anomalyLink));
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
