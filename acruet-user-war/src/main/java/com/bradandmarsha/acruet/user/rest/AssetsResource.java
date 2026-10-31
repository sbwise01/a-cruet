package com.bradandmarsha.acruet.user.rest;

import com.bradandmarsha.acruet.admin.OffboardService;
import com.bradandmarsha.acruet.auth.OidcUser;
import com.bradandmarsha.acruet.auth.UserSession;
import com.bradandmarsha.acruet.household.HouseholdJoinService;
import com.bradandmarsha.acruet.ui.AssetViews;
import com.bradandmarsha.acruet.ui.LedgerViews;
import com.bradandmarsha.acruet.ui.UserPageLayout;
import com.bradandmarsha.acruet.user.AcruetUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;

import java.util.Optional;

/**
 * Snapshot assets and liabilities page.
 */
@Path("assets")
public class AssetsResource {

    private final OffboardService offboardService = new OffboardService();

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response page(@Context HttpServletRequest request) {
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
        String banner = offboardService.status(user.get())
                .filter(status -> !status.exportComplete())
                .map(ignored -> LedgerViews.offboardBannerHtml())
                .orElse("");
        return Response.ok(UserPageLayout.renderAuthenticated(
                "Assets",
                AssetViews.assetsCss(),
                AssetViews.assetsMainHtml(banner, UserPageLayout.lockImageUrl()),
                UserPageLayout.navContext(oidcUser.get(), user.get(), true))).build();
    }
}
