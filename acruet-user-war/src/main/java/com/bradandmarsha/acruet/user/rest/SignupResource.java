package com.bradandmarsha.acruet.user.rest;

import com.bradandmarsha.acruet.signup.SignupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;

/**
 * Public signup and email verification (Phase 5).
 */
@Path("signup")
public class SignupResource {

    private final SignupService signupService = SignupService.fromEnvironment();

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String form() {
        return signupFormPage(null);
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public String submit(
            @FormParam("fullName") String fullName,
            @FormParam("email") String email,
            @FormParam("reason") String reason,
            @FormParam("phone") String phone,
            @FormParam("mailingAddress") String mailingAddress,
            @Context HttpServletRequest request) {
        SignupService.SubmitResult result = signupService.submit(
                new SignupService.SignupRequest(fullName, email, reason, phone, mailingAddress),
                clientIp(request),
                Instant.now());
        if (result.success()) {
            return successPage(result.message());
        }
        return signupFormPage(result.message());
    }

    @GET
    @Path("verify")
    @Produces(MediaType.TEXT_HTML)
    public Response verify(@QueryParam("token") String token) {
        SignupService.VerifyResult result = signupService.verify(token, Instant.now());
        if (result == SignupService.VerifyResult.PENDING_APPROVAL) {
            return Response.ok(verifiedPage()).build();
        }
        return Response.status(Response.Status.BAD_REQUEST).entity(invalidTokenPage()).build();
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String signupFormPage(String errorMessage) {
        String alert = errorMessage == null
                ? ""
                : """
                  <p class="error">%s</p>
                  """.formatted(escape(errorMessage));
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>Apply for a-cruet</title>
                  <style>
                    body { font-family: system-ui, sans-serif; margin: 2rem; line-height: 1.5; max-width: 40rem; }
                    label { display: block; margin-top: 1rem; font-weight: 600; }
                    input, textarea { width: 100%%; margin-top: 0.25rem; padding: 0.5rem; font: inherit; }
                    button { margin-top: 1.25rem; padding: 0.6rem 1.2rem; font: inherit; }
                    .error { color: #b91c1c; }
                    .hint { color: #444; font-size: 0.95rem; }
                  </style>
                </head>
                <body>
                  <h1>Apply for a-cruet</h1>
                  <p class="hint">Submit an application for admin review. You will verify your email before the queue step.</p>
                  %s
                  <form method="post" action="/signup">
                    <label for="fullName">Full name</label>
                    <input id="fullName" name="fullName" required>

                    <label for="email">Email</label>
                    <input id="email" name="email" type="email" required>

                    <label for="phone">Phone number</label>
                    <input id="phone" name="phone" required>

                    <label for="mailingAddress">Mailing address</label>
                    <textarea id="mailingAddress" name="mailingAddress" rows="3" required></textarea>

                    <label for="reason">Why do you want access?</label>
                    <textarea id="reason" name="reason" rows="4" required></textarea>

                    <button type="submit">Submit application</button>
                  </form>
                  <p class="hint"><a href="/">Return to sign-in</a></p>
                </body>
                </html>
                """.formatted(alert);
    }

    private static String successPage(String message) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>Verify your email</title>
                  <style>
                    body { font-family: system-ui, sans-serif; margin: 2rem; line-height: 1.5; max-width: 40rem; }
                  </style>
                </head>
                <body>
                  <h1>Check your email</h1>
                  <p>%s</p>
                  <p><a href="/signup">Submit another application</a></p>
                </body>
                </html>
                """.formatted(escape(message));
    }

    private static String verifiedPage() {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>Email verified</title>
                  <style>
                    body { font-family: system-ui, sans-serif; margin: 2rem; line-height: 1.5; max-width: 40rem; }
                  </style>
                </head>
                <body>
                  <h1>Email verified</h1>
                  <p>Your application is now <strong>pending admin approval</strong>. You will receive email when an administrator acts on your request.</p>
                  <p>No Keycloak account exists yet — sign-in is enabled only after approval.</p>
                </body>
                </html>
                """;
    }

    private static String invalidTokenPage() {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>Invalid verification link</title>
                  <style>
                    body { font-family: system-ui, sans-serif; margin: 2rem; line-height: 1.5; max-width: 40rem; }
                  </style>
                </head>
                <body>
                  <h1>Invalid verification link</h1>
                  <p>This verification link is invalid or has expired.</p>
                  <p><a href="/signup">Submit a new application</a></p>
                </body>
                </html>
                """;
    }

    private static String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
