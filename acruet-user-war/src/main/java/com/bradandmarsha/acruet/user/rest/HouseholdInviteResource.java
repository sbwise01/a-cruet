package com.bradandmarsha.acruet.user.rest;

import com.bradandmarsha.acruet.auth.OidcUser;
import com.bradandmarsha.acruet.auth.UserSession;
import com.bradandmarsha.acruet.household.HouseholdInviteService;
import com.bradandmarsha.acruet.household.HouseholdJoinService;
import com.bradandmarsha.acruet.ui.PageStyles;
import com.bradandmarsha.acruet.ui.UserNav;
import com.bradandmarsha.acruet.ui.UserPageLayout;
import com.bradandmarsha.acruet.user.AcruetUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Household member invite UI and API (Phase 12c).
 */
@Path("household")
public class HouseholdInviteResource {

    private final HouseholdInviteService inviteService = HouseholdInviteService.fromEnvironment();

    @GET
    @Path("invite")
    @Produces(MediaType.TEXT_HTML)
    public Response invitePage(@Context HttpServletRequest request) {
        Optional<OidcUser> oidcUser = UserSession.oidcUser(request);
        Optional<AcruetUser> user = UserSession.acruetUser(request);
        if (oidcUser.isEmpty() || user.isEmpty()) {
            return Response.seeOther(UriBuilder.fromPath("/auth/login").build()).build();
        }
        if (!user.get().keySetupComplete()) {
            return Response.seeOther(
                            UriBuilder.fromPath(HouseholdJoinService.initialKeySetupPath(user.get())).build())
                    .build();
        }
        return Response.ok(renderInvitePage(oidcUser.get(), user.get())).build();
    }

    @POST
    @Path("invite/api")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createInvite(HouseholdInviteRequest body, @Context HttpServletRequest request) {
        Optional<AcruetUser> user = UserSession.acruetUser(request);
        if (user.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        if (body == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "Request body required.")).build();
        }
        HouseholdInviteService.CreateResult result = inviteService.createInvite(
                user.get(),
                new HouseholdInviteService.InviteRequest(
                        body.email,
                        body.inviteToken,
                        body.wrappedDek,
                        body.wrapAlgorithm,
                        body.kdfAlgorithm,
                        body.kdfHash,
                        body.kdfSalt,
                        body.kdfIterations),
                Instant.now());
        if (!result.success()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", result.message()))
                    .build();
        }
        return Response.ok(Map.of(
                "sent", true,
                "message", result.message(),
                "email", result.email().orElse(""),
                "expiresAt", result.expiresAt().map(Instant::toString).orElse(""))).build();
    }

    private static String renderInvitePage(OidcUser oidcUser, AcruetUser user) {
        return UserPageLayout.renderAuthenticated(
                "Invite household member",
                PageStyles.formCss() + inviteCss(),
                inviteHtml(),
                UserPageLayout.navContext(oidcUser, user, false));
    }

    private static String inviteHtml() {
        return """
                <h2>Invite household member</h2>
                <p class="hint">Send an invitation to share your household ledger. Your encryption key must be unlocked.</p>
                <p class="hint">Households may have up to 5 members including pending invitations.</p>
                <div id="inviteLockedNotice" class="notice" hidden>
                  Unlock your encryption key before sending an invitation.
                  <a href="/keys/unlock?next=/household/invite">Unlock key</a>
                </div>
                <form id="inviteForm">
                  <label for="inviteEmail">Invitee email</label>
                  <input id="inviteEmail" type="email" autocomplete="email" required>
                  <p id="inviteError" class="error" hidden></p>
                  <p id="inviteSuccess" class="notice success" hidden></p>
                  <p class="actions">
                    <button type="submit" id="btnSendInvite">Send invitation</button>
                    <a href="/profile">Back to profile</a>
                  </p>
                </form>
                """
                + UserNav.keyPageScript("acruet-household-invite.js");
    }

    private static String inviteCss() {
        return """
                .notice.success {
                  border-left: 3px solid #34d399;
                }
                """;
    }

    public static class HouseholdInviteRequest {
        public String email;
        public String inviteToken;
        public String wrappedDek;
        public String wrapAlgorithm;
        public String kdfAlgorithm;
        public String kdfHash;
        public String kdfSalt;
        public int kdfIterations;
    }
}
