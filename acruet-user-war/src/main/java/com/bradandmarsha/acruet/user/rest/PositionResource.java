package com.bradandmarsha.acruet.user.rest;

import com.bradandmarsha.acruet.auth.UserSession;
import com.bradandmarsha.acruet.crypto.EncryptedBlob;
import com.bradandmarsha.acruet.position.FinancialPosition;
import com.bradandmarsha.acruet.position.FinancialPositionSnapshot;
import com.bradandmarsha.acruet.position.PositionException;
import com.bradandmarsha.acruet.position.PositionKind;
import com.bradandmarsha.acruet.position.PositionService;
import com.bradandmarsha.acruet.position.PositionStatus;
import com.bradandmarsha.acruet.user.AcruetUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Snapshot asset and liability JSON API.
 */
@Path("positions")
public class PositionResource {

    private final PositionService positionService = new PositionService();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listPositions(
            @QueryParam("includeArchived") @DefaultValue("false") boolean includeArchived,
            @Context HttpServletRequest request) {
        return withUser(request, user -> {
            List<FinancialPosition> listed = positionService.listPositions(user, includeArchived);
            List<Map<String, Object>> positions = listed.stream()
                    .map(this::positionJson)
                    .collect(Collectors.toList());
            long activeCount = listed.stream()
                    .filter(position -> position.status() == PositionStatus.ACTIVE)
                    .count();
            return Response.ok(Map.of(
                    "positions", positions,
                    "positionCount", activeCount,
                    "positionLimit", PositionService.MAX_ACTIVE_POSITIONS)).build();
        });
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createPosition(CreatePositionRequest body, @Context HttpServletRequest request) {
        return withUser(request, user -> {
            try {
                PositionKind kind = PositionKind.valueOf(body.kind);
                LocalDate asOfDate = LocalDate.parse(body.asOfDate);
                UUID groupId;
                try {
                    groupId = parseOptionalUuid(body.groupId);
                } catch (IllegalArgumentException exception) {
                    return badRequest("Invalid group.");
                }
                boolean investment = Boolean.TRUE.equals(body.investment);
                FinancialPosition position = positionService.createPosition(
                        user, kind, body.encryptedName, asOfDate, body.encryptedPayload, groupId, investment);
                return Response.status(Response.Status.CREATED).entity(positionJson(position)).build();
            } catch (IllegalArgumentException | NullPointerException exception) {
                return badRequest("Invalid kind or valuation date.");
            } catch (PositionException exception) {
                return badRequest(exception.getMessage());
            }
        });
    }

    @PUT
    @Path("{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateName(
            @PathParam("id") UUID positionId,
            UpdatePositionRequest body,
            @Context HttpServletRequest request) {
        return withUser(request, user -> {
            try {
                String encryptedName = body == null ? null : body.encryptedName;
                boolean groupSpecified = body != null && body.groupSpecified;
                boolean investmentSpecified = body != null && body.investmentSpecified;
                UUID groupId = body == null ? null : parseOptionalUuid(body.groupId);
                boolean investment = body != null && Boolean.TRUE.equals(body.investment);
                FinancialPosition position = positionService.updatePosition(
                        user,
                        positionId,
                        encryptedName,
                        groupSpecified,
                        groupId,
                        investmentSpecified,
                        investment);
                return Response.ok(positionJson(position)).build();
            } catch (IllegalArgumentException exception) {
                return badRequest("Invalid group.");
            } catch (PositionException exception) {
                return badRequest(exception.getMessage());
            }
        });
    }

    @GET
    @Path("{id}/snapshots")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listSnapshots(@PathParam("id") UUID positionId, @Context HttpServletRequest request) {
        return withUser(request, user -> {
            try {
                List<Map<String, Object>> snapshots = positionService.listSnapshots(user, positionId).stream()
                        .map(this::snapshotJson)
                        .collect(Collectors.toList());
                return Response.ok(Map.of("snapshots", snapshots)).build();
            } catch (PositionException exception) {
                return badRequest(exception.getMessage());
            }
        });
    }

    @POST
    @Path("{id}/snapshots")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addSnapshot(
            @PathParam("id") UUID positionId,
            CreateSnapshotRequest body,
            @Context HttpServletRequest request) {
        return withUser(request, user -> {
            try {
                LocalDate asOfDate = LocalDate.parse(body.asOfDate);
                FinancialPosition position = positionService.addSnapshot(
                        user, positionId, asOfDate, body.encryptedPayload);
                return Response.ok(positionJson(position)).build();
            } catch (IllegalArgumentException | NullPointerException exception) {
                return badRequest("Invalid valuation date.");
            } catch (PositionException exception) {
                return badRequest(exception.getMessage());
            }
        });
    }

    @POST
    @Path("{id}/archive")
    @Produces(MediaType.APPLICATION_JSON)
    public Response archivePosition(@PathParam("id") UUID positionId, @Context HttpServletRequest request) {
        return withUser(request, user -> {
            try {
                FinancialPosition position = positionService.archivePosition(user, positionId);
                return Response.ok(positionJson(position)).build();
            } catch (PositionException exception) {
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

    private Map<String, Object> positionJson(FinancialPosition position) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", position.id().toString());
        json.put("kind", position.kind().name());
        json.put("status", position.status().name());
        json.put("groupId", position.groupId() == null ? null : position.groupId().toString());
        json.put("investment", position.investment());
        json.put("encryptedName", EncryptedBlob.encode(position.encryptedName()));
        json.put("createdAt", position.createdAt().toString());
        json.put("updatedAt", position.updatedAt().toString());
        if (position.archivedAt() != null) {
            json.put("archivedAt", position.archivedAt().toString());
        }
        if (position.latestSnapshot() != null) {
            json.put("latestSnapshot", snapshotJson(position.latestSnapshot()));
        }
        return json;
    }

    private Map<String, Object> snapshotJson(FinancialPositionSnapshot snapshot) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", snapshot.id().toString());
        json.put("asOfDate", snapshot.asOfDate().toString());
        json.put("encryptedPayload", EncryptedBlob.encode(snapshot.encryptedPayload()));
        json.put("createdAt", snapshot.createdAt().toString());
        return json;
    }

    private static UUID parseOptionalUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }

    public static class CreatePositionRequest {
        public String kind;
        public String encryptedName;
        public String asOfDate;
        public String encryptedPayload;
        public String groupId;
        public Boolean investment;
    }

    public static class UpdatePositionRequest {
        public String encryptedName;
        boolean groupSpecified;
        String groupId;
        boolean investmentSpecified;
        Boolean investment;

        @JsonProperty("groupId")
        public void setGroupId(String value) {
            this.groupSpecified = true;
            this.groupId = value;
        }

        @JsonProperty("investment")
        public void setInvestment(Boolean value) {
            this.investmentSpecified = true;
            this.investment = value;
        }
    }

    public static class CreateSnapshotRequest {
        public String asOfDate;
        public String encryptedPayload;
    }
}
