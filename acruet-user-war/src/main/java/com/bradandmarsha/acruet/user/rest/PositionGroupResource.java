package com.bradandmarsha.acruet.user.rest;

import com.bradandmarsha.acruet.auth.UserSession;
import com.bradandmarsha.acruet.crypto.EncryptedBlob;
import com.bradandmarsha.acruet.position.PositionException;
import com.bradandmarsha.acruet.position.PositionGroup;
import com.bradandmarsha.acruet.position.PositionGroupService;
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

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Snapshot item group JSON API (PRODUCT.md §12).
 */
@Path("position-groups")
public class PositionGroupResource {

    private final PositionGroupService groupService = PositionGroupService.fromEnvironment();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listGroups(
            @QueryParam("includeArchived") @DefaultValue("false") boolean includeArchived,
            @Context HttpServletRequest request) {
        return withUser(request, user -> {
            List<PositionGroup> listed = groupService.list(user, includeArchived);
            List<Map<String, Object>> groups = listed.stream()
                    .map(this::groupJson)
                    .collect(Collectors.toList());
            int activeCount = (int) listed.stream()
                    .filter(group -> group.status() == PositionStatus.ACTIVE)
                    .count();
            return Response.ok(Map.of(
                    "groups", groups,
                    "groupCount", activeCount,
                    "groupLimit", PositionGroupService.MAX_ACTIVE_GROUPS)).build();
        });
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createGroup(CreateGroupRequest body, @Context HttpServletRequest request) {
        return withUser(request, user -> {
            try {
                String encryptedName = body == null ? null : body.encryptedName;
                PositionGroup group = groupService.create(user, encryptedName, Instant.now());
                return Response.status(Response.Status.CREATED).entity(groupJson(group)).build();
            } catch (PositionException exception) {
                return badRequest(exception.getMessage());
            }
        });
    }

    @PUT
    @Path("{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response renameGroup(
            @PathParam("id") UUID groupId,
            UpdateGroupRequest body,
            @Context HttpServletRequest request) {
        return withUser(request, user -> {
            try {
                String encryptedName = body == null ? null : body.encryptedName;
                PositionGroup group = groupService.rename(user, groupId, encryptedName);
                return Response.ok(groupJson(group)).build();
            } catch (PositionException exception) {
                return badRequest(exception.getMessage());
            }
        });
    }

    @POST
    @Path("{id}/archive")
    @Produces(MediaType.APPLICATION_JSON)
    public Response archiveGroup(@PathParam("id") UUID groupId, @Context HttpServletRequest request) {
        return withUser(request, user -> {
            try {
                PositionGroup group = groupService.archive(user, groupId);
                return Response.ok(groupJson(group)).build();
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

    private Map<String, Object> groupJson(PositionGroup group) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", group.id().toString());
        json.put("status", group.status().name());
        json.put("encryptedName", EncryptedBlob.encode(group.encryptedName()));
        json.put("createdAt", group.createdAt().toString());
        json.put("updatedAt", group.updatedAt().toString());
        if (group.archivedAt() != null) {
            json.put("archivedAt", group.archivedAt().toString());
        }
        return json;
    }

    public static class CreateGroupRequest {
        public String encryptedName;
    }

    public static class UpdateGroupRequest {
        public String encryptedName;
    }
}
