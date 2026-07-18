package com.bradandmarsha.acruet.user.rest;

import com.bradandmarsha.acruet.auth.OidcUser;
import com.bradandmarsha.acruet.auth.UserSession;
import com.bradandmarsha.acruet.keys.KeyService;
import com.bradandmarsha.acruet.keys.KeyServiceException;
import com.bradandmarsha.acruet.keys.WrappedDekPayload;
import com.bradandmarsha.acruet.ui.PageStyles;
import com.bradandmarsha.acruet.ui.UserPageLayout;
import com.bradandmarsha.acruet.user.AcruetUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;

import java.util.Map;
import java.util.Optional;

/**
 * Client-side encryption key setup, unlock, rotation, and wrapped DEK API (Phase 7).
 */
@Path("keys")
public class KeyResource {

    private final KeyService keyService = new KeyService();

    @GET
    @Path("setup")
    @Produces(MediaType.TEXT_HTML)
    public Response setupPage(@Context HttpServletRequest request) {
        Optional<OidcUser> oidcUser = UserSession.oidcUser(request);
        Optional<AcruetUser> user = requireUser(request);
        if (oidcUser.isEmpty() || user.isEmpty()) {
            return Response.seeOther(UriBuilder.fromPath("/auth/login").build()).build();
        }
        if (user.get().keySetupComplete()) {
            return Response.seeOther(UriBuilder.fromPath("/").build()).build();
        }
        return Response.ok(renderKeyPage(oidcUser.get(), user.get(), "Create encryption key", setupHtml())).build();
    }

    @GET
    @Path("unlock")
    @Produces(MediaType.TEXT_HTML)
    public Response unlockPage(@Context HttpServletRequest request) {
        Optional<OidcUser> oidcUser = UserSession.oidcUser(request);
        Optional<AcruetUser> user = requireUser(request);
        if (oidcUser.isEmpty() || user.isEmpty()) {
            return Response.seeOther(UriBuilder.fromPath("/auth/login").build()).build();
        }
        if (!user.get().keySetupComplete()) {
            return Response.seeOther(UriBuilder.fromPath("/keys/setup").build()).build();
        }
        return Response.ok(renderKeyPage(oidcUser.get(), user.get(), "Unlock encryption key", unlockHtml())).build();
    }

    @GET
    @Path("rotate")
    @Produces(MediaType.TEXT_HTML)
    public Response rotatePage(@Context HttpServletRequest request) {
        Optional<OidcUser> oidcUser = UserSession.oidcUser(request);
        Optional<AcruetUser> user = requireUser(request);
        if (oidcUser.isEmpty() || user.isEmpty()) {
            return Response.seeOther(UriBuilder.fromPath("/auth/login").build()).build();
        }
        if (!user.get().keySetupComplete()) {
            return Response.seeOther(UriBuilder.fromPath("/keys/setup").build()).build();
        }
        return Response.ok(renderKeyPage(oidcUser.get(), user.get(), "Rotate encryption key", rotateHtml())).build();
    }

    @GET
    @Path("status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response status(@Context HttpServletRequest request) {
        return withUser(request, user -> {
            KeyService.KeyStatus status = keyService.status(user);
            return Response.ok(Map.of(
                    "keySetupComplete", status.keySetupComplete(),
                    "hasWrappedDek", status.hasWrappedDek())).build();
        });
    }

    @GET
    @Path("wrapped-dek")
    @Produces(MediaType.APPLICATION_JSON)
    public Response wrappedDek(@Context HttpServletRequest request) {
        return withUser(request, user -> {
            Optional<KeyService.WrappedDekResponse> wrappedDek = keyService.wrappedDek(user);
            if (wrappedDek.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "No wrapped DEK stored."))
                        .build();
            }
            KeyService.WrappedDekResponse payload = wrappedDek.get();
            return Response.ok(Map.of(
                    "kdfAlgorithm", payload.kdfAlgorithm(),
                    "kdfHash", payload.kdfHash(),
                    "kdfSalt", payload.kdfSalt(),
                    "kdfIterations", payload.kdfIterations(),
                    "wrapAlgorithm", payload.wrapAlgorithm(),
                    "wrappedDek", payload.wrappedDek())).build();
        });
    }

    @PUT
    @Path("wrapped-dek")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response storeWrappedDek(WrappedDekRequest body, @Context HttpServletRequest request) {
        return withUser(request, user -> {
            try {
                keyService.storeInitialWrappedDek(user, toPayload(body));
                return Response.ok(Map.of("stored", true)).build();
            } catch (KeyServiceException exception) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", exception.getMessage()))
                        .build();
            }
        });
    }

    @POST
    @Path("confirm-recovery")
    @Produces(MediaType.APPLICATION_JSON)
    public Response confirmRecovery(@Context HttpServletRequest request) {
        return withUser(request, user -> {
            try {
                keyService.confirmRecovery(user);
                UserSession.markKeySetupComplete(request);
                return Response.ok(Map.of("keySetupComplete", true)).build();
            } catch (KeyServiceException exception) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", exception.getMessage()))
                        .build();
            }
        });
    }

    @POST
    @Path("rotate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response rotateWrappedDek(WrappedDekRequest body, @Context HttpServletRequest request) {
        return withUser(request, user -> {
            try {
                keyService.rotateWrappedDek(user, toPayload(body));
                return Response.ok(Map.of("rotated", true)).build();
            } catch (KeyServiceException exception) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", exception.getMessage()))
                        .build();
            }
        });
    }

    private Response withUser(
            HttpServletRequest request,
            java.util.function.Function<AcruetUser, Response> action) {
        Optional<AcruetUser> user = requireUser(request);
        if (user.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        return action.apply(user.get());
    }

    private Optional<AcruetUser> requireUser(HttpServletRequest request) {
        Optional<OidcUser> oidcUser = UserSession.oidcUser(request);
        if (oidcUser.isEmpty()) {
            return Optional.empty();
        }
        return keyService.findUser(oidcUser.get().subject());
    }

    private static WrappedDekPayload toPayload(WrappedDekRequest body) {
        return new WrappedDekPayload(
                body.kdfAlgorithm,
                body.kdfHash,
                body.kdfSalt,
                body.kdfIterations,
                body.wrapAlgorithm,
                body.wrappedDek);
    }

    private static String setupHtml() {
        return """
                <h2>Create your encryption key</h2>
                <p class="hint">Your passphrase never leaves this browser. a-cruet stores only a wrapped copy of your data key.</p>
                <div id="step-passphrase" class="wizard-step">
                  <p><strong>Step 1:</strong> Choose a strong passphrase (12+ characters).</p>
                  <p class="hint">Spaces and punctuation are allowed. Your passphrase never leaves this browser.</p>
                  <label for="passphrase">Passphrase</label>
                  <input id="passphrase" type="password" autocomplete="new-password" minlength="12" required>
                  <label for="passphraseConfirm">Confirm passphrase</label>
                  <input id="passphraseConfirm" type="password" autocomplete="new-password" minlength="12" required>
                  <p id="passphraseError" class="error" hidden></p>
                  <button type="button" id="btnPassphraseNext">Continue</button>
                </div>
                <div id="step-recovery" class="wizard-step" hidden>
                  <p><strong>Step 2:</strong> Download your recovery file and keep it somewhere safe.</p>
                  <p class="hint">Without your passphrase or this file, your ledger data cannot be recovered.</p>
                  <button type="button" id="btnDownloadRecovery">Download recovery file</button>
                  <p id="recoveryStatus" class="notice" hidden></p>
                  <label><input type="checkbox" id="recoveryConfirmed"> I saved my recovery file in a safe place</label>
                  <p id="recoveryError" class="error" hidden></p>
                  <button type="button" id="btnFinishSetup" disabled>Finish setup</button>
                </div>
                <p id="setupError" class="error" hidden></p>
                <script src="/static/js/acruet-key-setup.js"></script>
                """;
    }

    private static String unlockHtml() {
        return """
                <h2>Unlock your ledger</h2>
                <p class="hint">Enter your passphrase to decrypt data for this session (30-minute idle timeout).</p>
                <label for="passphrase">Passphrase</label>
                <input id="passphrase" type="password" autocomplete="current-password" required>
                <p id="unlockError" class="error" hidden></p>
                <button type="button" id="btnUnlock">Unlock</button>
                <p class="actions"><a href="/">Back to home</a></p>
                <script src="/static/js/acruet-key-unlock.js"></script>
                """;
    }

    private static String rotateHtml() {
        return """
                <h2>Rotate encryption key</h2>
                <p class="hint">Re-wraps your existing data key with a new passphrase. Ledger ciphertext is unchanged.</p>
                <label for="currentPassphrase">Current passphrase</label>
                <input id="currentPassphrase" type="password" autocomplete="current-password" required>
                <label for="newPassphrase">New passphrase</label>
                <input id="newPassphrase" type="password" autocomplete="new-password" minlength="12" required>
                <label for="newPassphraseConfirm">Confirm new passphrase</label>
                <input id="newPassphraseConfirm" type="password" autocomplete="new-password" minlength="12" required>
                <p id="rotateError" class="error" hidden></p>
                <button type="button" id="btnRotate">Rotate key</button>
                <p id="rotateSuccess" class="notice success" hidden></p>
                <p class="actions"><a href="/">Back to home</a></p>
                <script src="/static/js/acruet-key-rotate.js"></script>
                """;
    }

    private static String renderKeyPage(OidcUser oidcUser, AcruetUser user, String title, String mainHtml) {
        return UserPageLayout.renderAuthenticated(
                title,
                PageStyles.formCss() + wizardCss(),
                mainHtml,
                UserPageLayout.navContext(oidcUser, user, false));
    }

    private static String wizardCss() {
        return """
                .wizard-step { margin-top: 1rem; }
                .notice { margin-top: 1rem; padding: 0.75rem 1rem; border-radius: 8px; background: var(--bg-card); }
                """;
    }

    public static class WrappedDekRequest {
        public String kdfAlgorithm;
        public String kdfHash;
        public String kdfSalt;
        public int kdfIterations;
        public String wrapAlgorithm;
        public String wrappedDek;
    }
}
