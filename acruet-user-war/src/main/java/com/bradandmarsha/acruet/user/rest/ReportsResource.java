package com.bradandmarsha.acruet.user.rest;

import com.bradandmarsha.acruet.admin.OffboardService;
import com.bradandmarsha.acruet.auth.OidcUser;
import com.bradandmarsha.acruet.auth.UserSession;
import com.bradandmarsha.acruet.household.HouseholdJoinService;
import com.bradandmarsha.acruet.ui.LedgerViews;
import com.bradandmarsha.acruet.ui.ReportViews;
import com.bradandmarsha.acruet.ui.ReportViews.ReportPage;
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
 * Reports hub and dedicated report pages.
 */
@Path("reports")
public class ReportsResource {

    private final OffboardService offboardService = new OffboardService();

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response hub(@Context HttpServletRequest request) {
        return page(request, ReportPage.HUB);
    }

    @GET
    @Path("envelope-transactions")
    @Produces(MediaType.TEXT_HTML)
    public Response envelopeTransactions(@Context HttpServletRequest request) {
        return page(request, ReportPage.ENVELOPE_TRANSACTIONS);
    }

    @GET
    @Path("envelope-balance")
    @Produces(MediaType.TEXT_HTML)
    public Response envelopeBalance(@Context HttpServletRequest request) {
        return page(request, ReportPage.ENVELOPE_BALANCE);
    }

    @GET
    @Path("net-worth")
    @Produces(MediaType.TEXT_HTML)
    public Response netWorth(@Context HttpServletRequest request) {
        return page(request, ReportPage.NET_WORTH);
    }

    private Response page(HttpServletRequest request, ReportPage reportPage) {
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
                reportPage.title(),
                LedgerViews.ledgerCss() + ReportViews.reportsCss(),
                ReportViews.reportsPageHtml(
                        banner,
                        UserPageLayout.lockImageUrl(),
                        reportPage,
                        UserPageLayout.transactionsImageUrl(),
                        UserPageLayout.reportGraphsImageUrl(),
                        UserPageLayout.assetsImageUrl()),
                UserPageLayout.navContext(oidcUser.get(), user.get(), true))).build();
    }
}
