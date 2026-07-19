package com.bradandmarsha.acruet.user.rest;

import com.bradandmarsha.acruet.admin.OffboardService;
import com.bradandmarsha.acruet.auth.OidcUser;
import com.bradandmarsha.acruet.auth.UserSession;
import com.bradandmarsha.acruet.ui.PageStyles;
import com.bradandmarsha.acruet.ui.UserNav;
import com.bradandmarsha.acruet.ui.UserPageLayout;
import com.bradandmarsha.acruet.user.AcruetUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

/**
 * Client-side decrypted export during the offboard window (Phase 11).
 */
@Path("offboard")
public class OffboardResource {

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZoneId.systemDefault());

    private final OffboardService offboardService = new OffboardService();

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response page(@Context HttpServletRequest request) {
        Optional<OidcUser> oidcUser = UserSession.oidcUser(request);
        Optional<AcruetUser> user = UserSession.acruetUser(request);
        if (oidcUser.isEmpty() || user.isEmpty()) {
            return Response.seeOther(UriBuilder.fromPath("/auth/login").build()).build();
        }
        Optional<OffboardService.OffboardStatus> status = offboardService.status(user.get());
        if (status.isEmpty()) {
            return Response.seeOther(UriBuilder.fromPath("/").build()).build();
        }
        return Response.ok(renderPage(oidcUser.get(), user.get(), status.get())).build();
    }

    @GET
    @Path("api")
    @Produces(MediaType.APPLICATION_JSON)
    public Response status(@Context HttpServletRequest request) {
        return withUser(request, user -> {
            Optional<OffboardService.OffboardStatus> status = offboardService.status(user);
            if (status.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("active", false))
                        .build();
            }
            OffboardService.OffboardStatus offboard = status.get();
            return Response.ok(Map.of(
                    "active", true,
                    "exportDeadline", offboard.exportDeadline().toString(),
                    "exportComplete", offboard.exportComplete())).build();
        });
    }

    @POST
    @Path("api/complete")
    @Produces(MediaType.APPLICATION_JSON)
    public Response complete(@Context HttpServletRequest request) {
        return withUser(request, user -> {
            if (offboardService.status(user).isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "No active offboard export window."))
                        .build();
            }
            if (!offboardService.markExportComplete(user)) {
                return Response.status(Response.Status.CONFLICT)
                        .entity(Map.of("error", "Export was already marked complete."))
                        .build();
            }
            return Response.ok(Map.of("complete", true)).build();
        });
    }

    private Response withUser(
            HttpServletRequest request, java.util.function.Function<AcruetUser, Response> action) {
        Optional<AcruetUser> user = UserSession.acruetUser(request);
        if (user.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        return action.apply(user.get());
    }

    private static String renderPage(
            OidcUser oidcUser, AcruetUser user, OffboardService.OffboardStatus status) {
        String completedNotice = status.exportComplete()
                ? "<p class=\"notice success\">Export marked complete. Your data will be purged automatically.</p>"
                : "";
        return UserPageLayout.renderAuthenticated(
                "Export your data",
                PageStyles.formCss() + offboardCss(),
                """
                <h2>Export your a-cruet data</h2>
                <p class="hint">Your account is being offboarded. Download a decrypted copy of your ledger before <strong>%s</strong>.</p>
                %s
                <div id="offboardLocked">
                  <p>Unlock your ledger to decrypt export files.</p>
                  <label for="offboardPassphrase">Passphrase</label>
                  <input id="offboardPassphrase" type="password" autocomplete="current-password">
                  <p id="offboardUnlockError" class="error" hidden></p>
                  <button type="button" id="btnOffboardUnlock">Unlock</button>
                </div>
                <div id="offboardExport" hidden>
                  <p class="actions">
                    <button type="button" id="btnDownloadJson">Download JSON bundle</button>
                    <button type="button" id="btnDownloadCsv">Download transactions CSV</button>
                  </p>
                  <p id="offboardExportError" class="error" hidden></p>
                  <p class="hint">After you save your files locally, confirm export so purge can proceed.</p>
                  <button type="button" id="btnMarkComplete">I have saved my export</button>
                  <p id="offboardCompleteSuccess" class="notice success" hidden></p>
                </div>
                """
                        .formatted(
                                DISPLAY_TIME.format(status.exportDeadline()),
                                completedNotice)
                + UserNav.keyPageScript("acruet-offboard.js"),
                UserPageLayout.navContext(oidcUser, user, true));
    }

    private static String offboardCss() {
        return """
                .notice.success { border-left: 3px solid #34d399; }
                #offboardExport .actions button { margin-right: 0.75rem; }
                """;
    }
}
