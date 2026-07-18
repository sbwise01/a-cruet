package com.bradandmarsha.acruet.user.rest;

import com.bradandmarsha.acruet.auth.OidcUser;
import com.bradandmarsha.acruet.auth.UserSession;
import com.bradandmarsha.acruet.keys.KeyService;
import com.bradandmarsha.acruet.ui.AuthNavContext;
import com.bradandmarsha.acruet.ui.LedgerViews;
import com.bradandmarsha.acruet.ui.MarketingContent;
import com.bradandmarsha.acruet.ui.UserPageLayout;
import com.bradandmarsha.acruet.user.AcruetUser;
import com.bradandmarsha.acruet.user.UnlinkedLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import java.util.Optional;

/**
 * User landing page — public marketing for anonymous visitors; ledger home when signed in.
 */
@Path("/")
public class LandingResource {

    private final KeyService keyService = new KeyService();
    private final UnlinkedLoginService unlinkedLoginService = new UnlinkedLoginService();

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String index(@Context HttpServletRequest request) {
        Optional<OidcUser> user = UserSession.oidcUser(request);
        return user.map(oidcUser -> authenticatedPage(request, oidcUser)).orElseGet(this::publicPage);
    }

    private String publicPage() {
        return UserPageLayout.renderPublicMarketing(MarketingContent.html());
    }

    private String authenticatedPage(HttpServletRequest request, OidcUser oidcUser) {
        Optional<AcruetUser> acruetUser = keyService.findUser(oidcUser.subject());
        if (acruetUser.isEmpty()) {
            unlinkedLoginService.recordIfNeeded(request, oidcUser);
            AuthNavContext nav = UserPageLayout.navContext(oidcUser, null, false);
            return UserPageLayout.renderAuthenticated(
                    UserPageLayout.APP_NAME,
                    "",
                    """
                    <p>Your sign-in is not linked to an a-cruet account.</p>
                    <p class="hint">Administrators have been alerted. If you believe this is an error, sign out and contact support.</p>
                    """,
                    nav);
        }
        AcruetUser user = acruetUser.get();
        if (!user.keySetupComplete()) {
            AuthNavContext nav = UserPageLayout.navContext(oidcUser, user, false);
            return UserPageLayout.renderAuthenticated(
                    "Create encryption key",
                    "",
                    """
                    <p>Complete encryption key setup before using the ledger.</p>
                    <p class="actions"><a class="nav-btn" href="/keys/setup">Create encryption key</a></p>
                    """,
                    nav);
        }
        AuthNavContext nav = UserPageLayout.navContext(oidcUser, user, true);
        return UserPageLayout.renderAuthenticated(
                UserPageLayout.APP_NAME,
                LedgerViews.ledgerCss(),
                LedgerViews.ledgerMainHtml(UserPageLayout.lockImageUrl()),
                nav);
    }
}
