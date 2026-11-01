package com.bradandmarsha.acruet.user.rest;

import com.bradandmarsha.acruet.auth.UserSession;
import com.bradandmarsha.acruet.crypto.EncryptedBlob;
import com.bradandmarsha.acruet.networth.NetWorthException;
import com.bradandmarsha.acruet.networth.NetWorthService;
import com.bradandmarsha.acruet.networth.NetWorthSnapshot;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Household net-worth register JSON API (PRODUCT.md §11).
 */
@Path("net-worth")
public class NetWorthResource {

    private final NetWorthService netWorthService = NetWorthService.fromEnvironment();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response list(@Context HttpServletRequest request) {
        return withUser(request, user -> {
            List<Map<String, Object>> snapshots = netWorthService.list(user).stream()
                    .map(this::snapshotJson)
                    .collect(Collectors.toList());
            return Response.ok(Map.of(
                    "snapshots", snapshots,
                    "count", snapshots.size(),
                    "limit", NetWorthService.MAX_SNAPSHOTS)).build();
        });
    }

    @GET
    @Path("latest")
    @Produces(MediaType.APPLICATION_JSON)
    public Response latest(@Context HttpServletRequest request) {
        return withUser(request, user -> {
            Optional<NetWorthSnapshot> latest = netWorthService.latest(user);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("snapshot", latest.map(this::snapshotJson).orElse(null));
            body.put("count", netWorthService.count(user));
            body.put("limit", NetWorthService.MAX_SNAPSHOTS);
            return Response.ok(body).build();
        });
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(CreateRequest body, @Context HttpServletRequest request) {
        return withUser(request, user -> {
            try {
                LocalDate asOfDate = LocalDate.parse(body.asOfDate);
                NetWorthSnapshot snapshot = netWorthService.create(
                        user, asOfDate, body.encryptedPayload, Instant.now());
                return Response.status(Response.Status.CREATED).entity(snapshotJson(snapshot)).build();
            } catch (IllegalArgumentException | NullPointerException exception) {
                return badRequest("Invalid as-of date.");
            } catch (NetWorthException exception) {
                return badRequest(exception.getMessage());
            }
        });
    }

    private Response withUser(HttpServletRequest request, java.util.function.Function<AcruetUser, Response> action) {
        Optional<AcruetUser> user = UserSession.acruetUser(request);
        if (user.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        if (!user.get().keySetupComplete()) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "Complete encryption key setup first."))
                    .build();
        }
        return action.apply(user.get());
    }

    private static Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", message)).build();
    }

    private Map<String, Object> snapshotJson(NetWorthSnapshot snapshot) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", snapshot.id().toString());
        json.put("asOfDate", snapshot.asOfDate().toString());
        json.put("encryptedPayload", EncryptedBlob.encode(snapshot.encryptedPayload()));
        json.put("createdAt", snapshot.createdAt().toString());
        return json;
    }

    public static class CreateRequest {
        public String asOfDate;
        public String encryptedPayload;
    }
}
