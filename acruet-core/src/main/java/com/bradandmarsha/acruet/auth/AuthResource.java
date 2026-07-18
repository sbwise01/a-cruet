package com.bradandmarsha.acruet.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;

/**
 * OIDC callback and logout handlers shared by user and admin WARs.
 */
@Path("auth")
public class AuthResource {

    @GET
    @Path("login")
    public Response login(@Context HttpServletRequest request, @Context HttpServletResponse response) {
        OidcSettings settings = OidcSettings.fromEnvironment();
        if (!settings.isConfigured()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity("OIDC is not configured")
                    .build();
        }
        HttpSession session = request.getSession(true);
        String state = OidcService.newState();
        OidcStateSupport.save(request, response, session, state);
        OidcService service = new OidcService(settings);
        return Response.seeOther(UriBuilder.fromUri(service.beginAuthorizationUri(state)).build())
                .build();
    }

    @GET
    @Path("callback")
    public Response callback(
            @QueryParam("code") String code,
            @QueryParam("state") String state,
            @Context HttpServletRequest request,
            @Context HttpServletResponse response) {
        OidcSettings settings = OidcSettings.fromEnvironment();
        if (!settings.isConfigured()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity("OIDC is not configured")
                    .build();
        }
        if (code == null || code.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Missing authorization code")
                    .build();
        }

        HttpSession session = request.getSession(true);
        if (!OidcStateSupport.matches(request, session, state)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Invalid OIDC state")
                    .build();
        }
        OidcStateSupport.clear(request, response, session);

        OidcService service = new OidcService(settings);
        OidcUser user = service.completeAuthorization(code);
        session.setAttribute(OidcUser.SESSION_ATTRIBUTE, user);

        if (settings.requireAdminRole() && !user.hasRealmRole(settings.adminRole())) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("Administrator role required")
                    .build();
        }

        if (!settings.requireAdminRole()) {
            UserSession.onLogin(request, user);
            if (UserSession.acruetUser(request).isPresent() && !UserSession.isKeySetupComplete(request)) {
                return Response.seeOther(UriBuilder.fromPath("/keys/setup").build()).build();
            }
        }

        return Response.seeOther(UriBuilder.fromPath("/").build()).build();
    }

    @GET
    @Path("logout")
    public Response logout(@Context HttpServletRequest request) {
        OidcSettings settings = OidcSettings.fromEnvironment();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        if (!settings.isConfigured()) {
            return Response.seeOther(UriBuilder.fromPath("/").build()).build();
        }
        OidcService service = new OidcService(settings);
        return Response.seeOther(UriBuilder.fromUri(service.logoutRedirectUri()).build()).build();
    }

    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    public Response me(@Context HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        Object userObject = session.getAttribute(OidcUser.SESSION_ATTRIBUTE);
        if (!(userObject instanceof OidcUser user)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        return Response.ok(java.util.Map.of(
                "subject", user.subject(),
                "username", user.displayName(),
                "roles", user.realmRoles())).build();
    }
}
