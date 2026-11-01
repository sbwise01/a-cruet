package com.bradandmarsha.acruet.admin.rest;

import com.bradandmarsha.acruet.approval.ApprovalService;
import com.bradandmarsha.acruet.ui.AdminPageLayout;
import jakarta.servlet.http.HttpServletRequest;
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
        return AdminPageLayout.renderAuthenticated(
                AdminPageLayout.APP_NAME,
                "",
                """
                %s
                %s
                <ul>
                  <li><a href="/approvals">Approval queue</a></li>
                  <li><a href="/users">User list</a></li>
                  <li><a href="/blocked-signups">Blocked signups</a></li>
                  <li><a href="/anomalies">Login anomalies</a></li>
                </ul>
                """
                        .formatted(queueLink, anomalyLink),
                AdminWebSupport.currentUser(request).orElse(null),
                false);
    }
}
