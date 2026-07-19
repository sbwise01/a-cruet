package com.bradandmarsha.acruet.admin.rest;

import com.bradandmarsha.acruet.approval.ApprovalService;
import com.bradandmarsha.acruet.auth.OidcUser;
import com.bradandmarsha.acruet.signup.SignupRepository;
import com.bradandmarsha.acruet.ui.AdminPageLayout;
import com.bradandmarsha.acruet.ui.PageStyles;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
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
 * Administrator signup approval queue (Phase 6).
 */
@Path("approvals")
public class AdminApprovalResource {

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZoneId.systemDefault());

    private final ApprovalService approvalService = ApprovalService.fromEnvironment();

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String queue(@Context HttpServletRequest request) {
        List<SignupRepository.PendingApplication> pending = approvalService.listPending();
        String notice = flashNotice(request);
        return AdminPageLayout.render(
                "Approval queue",
                PageStyles.tableCss(),
                """
                <h2>Pending applications</h2>
                <p class="hint">Verified applicants awaiting Keycloak provisioning.</p>
                %s
                %s
                <p><a href="/">Back to dashboard</a></p>
                """
                        .formatted(notice, pendingTable(pending)));
    }

    @POST
    @Path("{id}/approve")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response approve(@Context HttpServletRequest request, @PathParam("id") UUID applicationId) {
        return act(request, applicationId, true);
    }

    @POST
    @Path("{id}/reject")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response reject(@Context HttpServletRequest request, @PathParam("id") UUID applicationId) {
        return act(request, applicationId, false);
    }

    private Response act(HttpServletRequest request, UUID applicationId, boolean approve) {
        Optional<OidcUser> admin = currentUser(request);
        if (admin.isEmpty()) {
            return Response.seeOther(URI.create("/auth/login")).build();
        }

        ApprovalService.AdminActor actor = new ApprovalService.AdminActor(
                admin.get().subject(), admin.get().email());
        ApprovalService.ActionResult result = approve
                ? approvalService.approve(applicationId, actor)
                : approvalService.reject(applicationId, actor);
        setFlash(request, result.success(), result.message());
        return Response.seeOther(URI.create("/approvals")).build();
    }

    private static String pendingTable(List<SignupRepository.PendingApplication> pending) {
        if (pending.isEmpty()) {
            return "<p class=\"hint\">No applications are waiting for approval.</p>";
        }
        StringBuilder rows = new StringBuilder();
        for (SignupRepository.PendingApplication application : pending) {
            String verified = application.verifiedAt()
                    .map(DISPLAY_TIME::format)
                    .orElse("—");
            rows.append(
                    """
                    <tr>
                      <td>
                        <strong>%s</strong><br>
                        <span class="meta">%s</span>
                        %s
                      </td>
                      <td>%s</td>
                      <td class="meta">%s</td>
                      <td class="row-actions">
                        <form method="post" action="/approvals/%s/approve">
                          <button type="submit">Approve</button>
                        </form>
                        <form method="post" action="/approvals/%s/reject">
                          <button type="submit" class="reject">Reject</button>
                        </form>
                      </td>
                    </tr>
                    """
                            .formatted(
                                    escape(application.fullName()),
                                    escape(application.email()),
                                    application.householdInviteId().isPresent()
                                            ? "<br><span class=\"meta\">Joins existing household (invite)</span>"
                                            : "",
                                    escape(application.reason()),
                                    verified,
                                    application.id(),
                                    application.id()));
        }
        return """
                <table>
                  <thead>
                    <tr>
                      <th>Applicant</th>
                      <th>Reason</th>
                      <th>Verified</th>
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

    private static String flashNotice(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return "";
        }
        Object successObject = session.getAttribute("acruet.admin.flash.success");
        Object messageObject = session.getAttribute("acruet.admin.flash.message");
        session.removeAttribute("acruet.admin.flash.success");
        session.removeAttribute("acruet.admin.flash.message");
        if (!(messageObject instanceof String message) || message.isBlank()) {
            return "";
        }
        boolean success = Boolean.TRUE.equals(successObject);
        String cssClass = success ? "success" : "error";
        return "<div class=\"notice " + cssClass + "\">" + escape(message) + "</div>";
    }

    private static void setFlash(HttpServletRequest request, boolean success, String message) {
        HttpSession session = request.getSession(true);
        session.setAttribute("acruet.admin.flash.success", success);
        session.setAttribute("acruet.admin.flash.message", message);
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
