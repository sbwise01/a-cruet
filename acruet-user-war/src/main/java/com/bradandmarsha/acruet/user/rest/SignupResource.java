package com.bradandmarsha.acruet.user.rest;

import com.bradandmarsha.acruet.household.HouseholdInviteService;
import com.bradandmarsha.acruet.signup.SignupService;
import com.bradandmarsha.acruet.ui.PageStyles;
import com.bradandmarsha.acruet.ui.UserPageLayout;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Public signup and email verification (Phase 5, household invite Phase 12c).
 */
@Path("signup")
public class SignupResource {

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private final SignupService signupService = SignupService.fromEnvironment();
    private final HouseholdInviteService householdInviteService = HouseholdInviteService.fromEnvironment();

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response form(@QueryParam("invite") String inviteToken) {
        if (inviteToken != null && !inviteToken.isBlank()) {
            Optional<HouseholdInviteService.SignupInvitePreview> preview =
                    householdInviteService.previewSignupInvite(inviteToken, Instant.now());
            if (preview.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST).entity(invalidInvitePage()).build();
            }
            return Response.ok(signupFormPage(null, preview.get(), inviteToken)).build();
        }
        return Response.ok(signupFormPage(null, null, null)).build();
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
            @FormParam("inviteToken") String inviteToken,
            @Context HttpServletRequest request) {
        Optional<String> inviteOptional =
                inviteToken == null || inviteToken.isBlank() ? Optional.empty() : Optional.of(inviteToken);
        SignupService.SubmitResult result = signupService.submit(
                new SignupService.SignupRequest(fullName, email, reason, phone, mailingAddress),
                clientIp(request),
                Instant.now(),
                inviteOptional);
        if (result.success()) {
            return successPage(result.message());
        }
        Optional<HouseholdInviteService.SignupInvitePreview> preview = inviteOptional.flatMap(
                token -> householdInviteService.previewSignupInvite(token, Instant.now()));
        return signupFormPage(result.message(), preview.orElse(null), inviteToken);
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

    private static String signupFormPage(
            String errorMessage,
            HouseholdInviteService.SignupInvitePreview invitePreview,
            String inviteToken) {
        String alert = errorMessage == null
                ? ""
                : """
                  <p class="error">%s</p>
                  """.formatted(escape(errorMessage));

        boolean invited = invitePreview != null;
        String inviteBanner = invited
                ? """
                  <p class="notice">You are applying to join an existing a-cruet household. Use the invited email address <strong>%s</strong>. Invitation valid until %s.</p>
                  """
                        .formatted(
                                escape(invitePreview.email()),
                                DISPLAY_TIME.format(invitePreview.expiresAt()))
                : "";
        String emailField = invited
                ? """
                  <label for="email">Email</label>
                  <input id="email" name="email" type="email" value="%s" readonly required>
                  """
                        .formatted(escapeAttr(invitePreview.email()))
                : """
                  <label for="email">Email</label>
                  <input id="email" name="email" type="email" required>
                  """;
        String hiddenInvite = invited && inviteToken != null
                ? "<input type=\"hidden\" name=\"inviteToken\" value=\"" + escapeAttr(inviteToken) + "\">"
                : "";

        return UserPageLayout.render(
                invited ? "Apply to join household" : "Apply for a-cruet",
                PageStyles.formCss(),
                """
                <h2>%s</h2>
                <p class="hint">Submit an application for admin review. You will verify your email before the queue step.</p>
                %s
                %s
                <form method="post" action="/signup">
                  %s
                  <label for="fullName">Full name</label>
                  <input id="fullName" name="fullName" required>

                  %s

                  <label for="phone">Phone number</label>
                  <input id="phone" name="phone" required>

                  <label for="mailingAddress">Mailing address</label>
                  <textarea id="mailingAddress" name="mailingAddress" rows="3" required></textarea>

                  <label for="reason">Why do you want access?</label>
                  <textarea id="reason" name="reason" rows="4" required></textarea>

                  <button type="submit">Submit application</button>
                </form>
                <p class="hint"><a href="/">Return to home</a></p>
                """
                        .formatted(
                                invited ? "Apply to join household" : "Apply for access",
                                inviteBanner,
                                alert,
                                hiddenInvite,
                                emailField));
    }

    private static String invalidInvitePage() {
        return simplePage(
                "Invalid household invitation",
                "Invalid household invitation",
                """
                <p>This household invitation link is invalid or has expired.</p>
                <p><a href="/signup">Apply without an invitation</a></p>
                """);
    }

    private static String successPage(String message) {
        return simplePage(
                "Verify your email",
                "Check your email",
                """
                <p>%s</p>
                <p><a href="/signup">Submit another application</a></p>
                """.formatted(escape(message)));
    }

    private static String verifiedPage() {
        return simplePage(
                "Email verified",
                "Email verified",
                """
                <p>Your application is now <strong>pending admin approval</strong>. You will receive email when an administrator acts on your request.</p>
                <p class="hint">No Keycloak account exists yet — sign-in is enabled only after approval.</p>
                """);
    }

    private static String invalidTokenPage() {
        return simplePage(
                "Invalid verification link",
                "Invalid verification link",
                """
                <p>This verification link is invalid or has expired.</p>
                <p><a href="/signup">Submit a new application</a></p>
                """);
    }

    private static String simplePage(String title, String heading, String bodyHtml) {
        return UserPageLayout.render(
                title,
                """
                <h2>%s</h2>
                %s
                """.formatted(escape(heading), bodyHtml));
    }

    private static String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String escapeAttr(String value) {
        return escape(value).replace("'", "&#39;");
    }
}
