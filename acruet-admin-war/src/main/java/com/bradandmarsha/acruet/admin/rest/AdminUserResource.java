package com.bradandmarsha.acruet.admin.rest;

import com.bradandmarsha.acruet.admin.AdminOpsService;
import com.bradandmarsha.acruet.approval.ApprovalService;
import com.bradandmarsha.acruet.user.AcruetUser;
import com.bradandmarsha.acruet.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Provisioned user list with operational counts and admin actions (Phase 11).
 */
@Path("users")
public class AdminUserResource {

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZoneId.systemDefault());

    private final AdminOpsService adminOpsService = AdminOpsService.fromEnvironment();

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String list(@Context HttpServletRequest request) {
        List<AdminOpsService.OperationalUserView> users = adminOpsService.listOperationalUsers();
        return AdminWebSupport.adminShell(
                "Users",
                AdminWebSupport.flashNotice(request),
                """
                <h2>Provisioned users</h2>
                <p class="hint">Operational metadata only — ledger content stays encrypted on the client.</p>
                %s
                """
                        .formatted(userTable(users)));
    }

    @POST
    @Path("{id}/grant-admin")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response grantAdmin(@Context HttpServletRequest request, @PathParam("id") UUID userId) {
        return act(request, userId, admin -> adminOpsService.grantAdmin(userId, admin));
    }

    @POST
    @Path("{id}/revoke-admin")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response revokeAdmin(@Context HttpServletRequest request, @PathParam("id") UUID userId) {
        return act(request, userId, admin -> adminOpsService.revokeAdmin(userId, admin));
    }

    @POST
    @Path("{id}/suspend")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response suspend(
            @Context HttpServletRequest request,
            @PathParam("id") UUID userId,
            @FormParam("days") @DefaultValue("7") int days) {
        return act(request, userId, admin -> adminOpsService.suspend(userId, days, admin));
    }

    @POST
    @Path("{id}/offboard")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response offboard(@Context HttpServletRequest request, @PathParam("id") UUID userId) {
        return act(request, userId, admin -> adminOpsService.offboard(userId, admin));
    }

    private Response act(
            HttpServletRequest request,
            UUID userId,
            java.util.function.Function<ApprovalService.AdminActor, ApprovalService.ActionResult> action) {
        Optional<ApprovalService.AdminActor> admin = AdminWebSupport.currentActor(request);
        if (admin.isEmpty()) {
            return Response.seeOther(URI.create("/auth/login")).build();
        }
        ApprovalService.ActionResult result = action.apply(admin.get());
        AdminWebSupport.setFlash(request, result.success(), result.message());
        return Response.seeOther(URI.create("/users")).build();
    }

    private static String userTable(List<AdminOpsService.OperationalUserView> users) {
        if (users.isEmpty()) {
            return "<p class=\"hint\">No provisioned users yet.</p>";
        }
        StringBuilder rows = new StringBuilder();
        Instant now = Instant.now();
        for (AdminOpsService.OperationalUserView view : users) {
            UserRepository.OperationalUserRow row = view.row();
            AcruetUser user = row.user();
            rows.append(
                    """
                    <tr>
                      <td>
                        <strong>%s</strong><br>
                        <span class="meta">%s</span>
                      </td>
                      <td>%d</td>
                      <td>%d</td>
                      <td>%s</td>
                      <td>%s</td>
                      <td class="row-actions">%s</td>
                    </tr>
                    """
                            .formatted(
                                    AdminWebSupport.escape(user.displayName()),
                                    AdminWebSupport.escape(user.email()),
                                    user.ledgerAccountCount(),
                                    user.transactionCount(),
                                    formatInstant(user.lastLoginAt()),
                                    formatInstant(latestActivity(user)),
                                    statusTags(row, now),
                                    actionForms(user.id(), view.adminRoleGranted(), row, now)));
        }
        return """
                <table>
                  <thead>
                    <tr>
                      <th>User</th>
                      <th>Envelopes</th>
                      <th>Transactions</th>
                      <th>Last login</th>
                      <th>Last activity</th>
                      <th>Status</th>
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

    private static String statusTags(UserRepository.OperationalUserRow row, Instant now) {
        StringBuilder tags = new StringBuilder();
        if (row.offboardDeadline() != null && row.offboardExportCompletedAt() == null) {
            tags.append("<span class=\"status-tag warn\">Offboarding</span> ");
        } else if (row.offboardExportCompletedAt() != null) {
            tags.append("<span class=\"status-tag warn\">Export done</span> ");
        }
        if (row.suspendedUntil() != null && row.suspendedUntil().isAfter(now)) {
            tags.append("<span class=\"status-tag warn\">Suspended</span>");
        } else if (tags.isEmpty()) {
            tags.append("<span class=\"status-tag\">Active</span>");
        }
        return tags.toString().trim();
    }

    private static String actionForms(
            UUID userId,
            boolean adminRoleGranted,
            UserRepository.OperationalUserRow row,
            Instant now) {
        boolean offboarding = row.offboardDeadline() != null && row.offboardExportCompletedAt() == null;
        boolean suspended = row.suspendedUntil() != null && row.suspendedUntil().isAfter(now);
        StringBuilder forms = new StringBuilder();
        if (adminRoleGranted) {
            forms.append(
                    """
                    <form method="post" action="/users/%s/revoke-admin">
                      <button type="submit" class="reject">Revoke admin</button>
                    </form>
                    """
                            .formatted(userId));
        } else {
            forms.append(
                    """
                    <form method="post" action="/users/%s/grant-admin">
                      <button type="submit">Grant admin</button>
                    </form>
                    """
                            .formatted(userId));
        }
        if (!suspended) {
            forms.append(
                    """
                    <form method="post" action="/users/%s/suspend">
                      <input type="number" name="days" min="1" max="365" value="7" aria-label="Suspension days">
                      <button type="submit" class="reject">Suspend</button>
                    </form>
                    """
                            .formatted(userId));
        }
        if (!offboarding) {
            forms.append(
                    """
                    <form method="post" action="/users/%s/offboard">
                      <button type="submit" class="reject">Offboard</button>
                    </form>
                    """
                            .formatted(userId));
        }
        return forms.toString();
    }

    private static Instant latestActivity(AcruetUser user) {
        Instant lastLogin = user.lastLoginAt();
        Instant lastTransaction = user.lastTransactionAt();
        if (lastLogin == null) {
            return lastTransaction;
        }
        if (lastTransaction == null) {
            return lastLogin;
        }
        return lastLogin.isAfter(lastTransaction) ? lastLogin : lastTransaction;
    }

    private static String formatInstant(Instant instant) {
        return instant == null ? "—" : DISPLAY_TIME.format(instant);
    }
}
