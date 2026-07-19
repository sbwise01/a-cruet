package com.bradandmarsha.acruet.admin.rest;

import com.bradandmarsha.acruet.admin.AdminOpsService;
import com.bradandmarsha.acruet.user.LoginAnomalyRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Unlinked Keycloak login anomaly queue (Phase 11 task 9).
 */
@Path("anomalies")
public class AdminAnomalyResource {

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZoneId.systemDefault());

    private final AdminOpsService adminOpsService = AdminOpsService.fromEnvironment();

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String list(@Context HttpServletRequest request) {
        List<LoginAnomalyRepository.LoginAnomaly> anomalies = adminOpsService.listRecentAnomalies(100);
        return AdminWebSupport.adminShell(
                "Login anomalies",
                AdminWebSupport.flashNotice(request),
                """
                <h2>Unlinked login anomalies</h2>
                <p class="hint">Keycloak sessions on the user app with no matching <code>acruet_user</code> row.</p>
                %s
                """
                        .formatted(anomalyTable(anomalies)));
    }

    private static String anomalyTable(List<LoginAnomalyRepository.LoginAnomaly> anomalies) {
        if (anomalies.isEmpty()) {
            return "<p class=\"hint\">No anomalies recorded.</p>";
        }
        StringBuilder rows = new StringBuilder();
        for (LoginAnomalyRepository.LoginAnomaly anomaly : anomalies) {
            String alertStatus = anomaly.alertedAt() == null
                    ? "<span class=\"status-tag warn\">Unalerted</span>"
                    : "<span class=\"status-tag\">Alerted</span>";
            rows.append(
                    """
                    <tr>
                      <td class="meta">%s</td>
                      <td>%s<br><span class="meta">%s</span></td>
                      <td>%s</td>
                      <td>%s</td>
                    </tr>
                    """
                            .formatted(
                                    DISPLAY_TIME.format(anomaly.createdAt()),
                                    AdminWebSupport.escape(anomaly.email()),
                                    AdminWebSupport.escape(anomaly.keycloakUserId()),
                                    AdminWebSupport.escape(anomaly.detail()),
                                    alertStatus));
        }
        return """
                <table>
                  <thead>
                    <tr>
                      <th>When</th>
                      <th>Identity</th>
                      <th>Detail</th>
                      <th>Alert</th>
                    </tr>
                  </thead>
                  <tbody>
                %s
                  </tbody>
                </table>
                """
                .formatted(rows);
    }
}
