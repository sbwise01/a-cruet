package com.bradandmarsha.acruet.admin.rest;

import com.bradandmarsha.acruet.admin.AdminOpsService;
import com.bradandmarsha.acruet.config.CronSettings;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * In-cluster CronJob callbacks (Phase 11). Protected by {@link CronSettings}.
 */
@Path("internal/cron")
public class AdminCronResource {

    private static final String CRON_SECRET_HEADER = "X-Acruet-Cron-Secret";

    private final AdminOpsService adminOpsService = AdminOpsService.fromEnvironment();

    @POST
    @Path("unsuspend")
    @Produces(MediaType.APPLICATION_JSON)
    public Response unsuspend(@HeaderParam(CRON_SECRET_HEADER) String secret) {
        if (!CronSettings.matches(secret)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        AdminOpsService.CronResult result = adminOpsService.runAutoUnsuspend();
        return Response.ok(jsonBody("unsuspended", result)).build();
    }

    @POST
    @Path("purge-offboard")
    @Produces(MediaType.APPLICATION_JSON)
    public Response purgeOffboard(@HeaderParam(CRON_SECRET_HEADER) String secret) {
        if (!CronSettings.matches(secret)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        AdminOpsService.CronResult result = adminOpsService.runOffboardPurge();
        return Response.ok(jsonBody("purged", result)).build();
    }

    @POST
    @Path("anomaly-alerts")
    @Produces(MediaType.APPLICATION_JSON)
    public Response anomalyAlerts(@HeaderParam(CRON_SECRET_HEADER) String secret) {
        if (!CronSettings.matches(secret)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        int delivered = adminOpsService.deliverPendingAnomalyAlerts();
        return Response.ok(Map.of("delivered", delivered)).build();
    }

    private static Map<String, Object> jsonBody(String countKey, AdminOpsService.CronResult result) {
        return Map.of(countKey, result.processed(), "errors", result.errors());
    }
}
