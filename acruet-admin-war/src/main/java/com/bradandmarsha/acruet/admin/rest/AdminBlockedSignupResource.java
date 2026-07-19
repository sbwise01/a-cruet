package com.bradandmarsha.acruet.admin.rest;

import com.bradandmarsha.acruet.admin.AdminOpsService;
import com.bradandmarsha.acruet.approval.ApprovalService;
import com.bradandmarsha.acruet.signup.SignupApplication;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Unblock twice-rejected signup emails (Phase 11).
 */
@Path("blocked-signups")
public class AdminBlockedSignupResource {

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZoneId.systemDefault());

    private final AdminOpsService adminOpsService = AdminOpsService.fromEnvironment();

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String list(@Context HttpServletRequest request) {
        List<SignupApplication> blocked = adminOpsService.listBlockedSignups();
        return AdminWebSupport.adminShell(
                "Blocked signups",
                AdminWebSupport.flashNotice(request),
                """
                <h2>Blocked signup emails</h2>
                <p class="hint">Applicants rejected twice cannot re-apply until unblocked.</p>
                %s
                """
                        .formatted(blockedTable(blocked)));
    }

    @POST
    @Path("{id}/unblock")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response unblock(@Context HttpServletRequest request, @PathParam("id") UUID applicationId) {
        Optional<ApprovalService.AdminActor> admin = AdminWebSupport.currentActor(request);
        if (admin.isEmpty()) {
            return Response.seeOther(URI.create("/auth/login")).build();
        }
        ApprovalService.ActionResult result = adminOpsService.unblockSignup(applicationId, admin.get());
        AdminWebSupport.setFlash(request, result.success(), result.message());
        return Response.seeOther(URI.create("/blocked-signups")).build();
    }

    private static String blockedTable(List<SignupApplication> blocked) {
        if (blocked.isEmpty()) {
            return "<p class=\"hint\">No blocked signup emails.</p>";
        }
        StringBuilder rows = new StringBuilder();
        for (SignupApplication application : blocked) {
            rows.append(
                    """
                    <tr>
                      <td>
                        <strong>%s</strong><br>
                        <span class="meta">%s</span>
                      </td>
                      <td>%d</td>
                      <td class="meta">%s</td>
                      <td class="row-actions">
                        <form method="post" action="/blocked-signups/%s/unblock">
                          <button type="submit">Unblock</button>
                        </form>
                      </td>
                    </tr>
                    """
                            .formatted(
                                    AdminWebSupport.escape(application.fullName()),
                                    AdminWebSupport.escape(application.email()),
                                    application.rejectionCount(),
                                    DISPLAY_TIME.format(application.createdAt()),
                                    application.id()));
        }
        return """
                <table>
                  <thead>
                    <tr>
                      <th>Applicant</th>
                      <th>Rejections</th>
                      <th>Applied</th>
                      <th>Actions</th>
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
