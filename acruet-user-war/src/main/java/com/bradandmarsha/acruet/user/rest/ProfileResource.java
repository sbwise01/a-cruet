package com.bradandmarsha.acruet.user.rest;

import com.bradandmarsha.acruet.auth.OidcUser;
import com.bradandmarsha.acruet.auth.UserSession;
import com.bradandmarsha.acruet.ui.PageStyles;
import com.bradandmarsha.acruet.ui.UserNav;
import com.bradandmarsha.acruet.ui.UserPageLayout;
import com.bradandmarsha.acruet.user.AcruetUser;
import com.bradandmarsha.acruet.user.UserProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
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
 * User profile contact fields and ledger preferences.
 */
@Path("profile")
public class ProfileResource {

    private final UserProfileService profileService = new UserProfileService();

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response profilePage(@Context HttpServletRequest request) {
        Optional<OidcUser> oidcUser = UserSession.oidcUser(request);
        Optional<AcruetUser> user = requireUser(request);
        if (oidcUser.isEmpty() || user.isEmpty()) {
            return Response.seeOther(UriBuilder.fromPath("/auth/login").build()).build();
        }
        return Response.ok(renderProfilePage(oidcUser.get(), user.get())).build();
    }

    @GET
    @Path("api")
    @Produces(MediaType.APPLICATION_JSON)
    public Response profileData(@Context HttpServletRequest request) {
        return withUser(request, user -> {
            UserProfileService.ProfileView profile = profileService.profile(user);
            return Response.ok(Map.of(
                    "displayName", profile.displayName(),
                    "email", profile.email(),
                    "phone", profile.phone(),
                    "mailingAddress", profile.mailingAddress(),
                    "allowNegativeWithdraw", profile.allowNegativeWithdraw())).build();
        });
    }

    @PUT
    @Path("api")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateProfile(ProfileUpdateRequest body, @Context HttpServletRequest request) {
        return withUser(request, user -> {
            try {
                UserProfileService.ProfileUpdate update = new UserProfileService.ProfileUpdate(
                        body.displayName,
                        body.phone,
                        body.mailingAddress,
                        body.allowNegativeWithdraw);
                profileService.updateProfile(user, update);
                return Response.ok(Map.of("saved", true)).build();
            } catch (UserProfileService.UserProfileException exception) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", exception.getMessage()))
                        .build();
            }
        });
    }

    private Response withUser(
            HttpServletRequest request, java.util.function.Function<AcruetUser, Response> action) {
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
        return profileService.findUser(oidcUser.get().subject());
    }

    private static String renderProfilePage(OidcUser oidcUser, AcruetUser user) {
        return UserPageLayout.renderAuthenticated(
                "Profile",
                PageStyles.formCss() + profileCss(),
                profileHtml(),
                UserPageLayout.navContext(oidcUser, user, false));
    }

    private static String profileHtml() {
        return """
                <h2>Profile</h2>
                <p class="hint">Update your contact information and ledger preferences.</p>
                <label for="displayName">Full name</label>
                <input id="displayName" type="text" required autocomplete="name">
                <label for="email">Email</label>
                <input id="email" type="email" disabled autocomplete="email">
                <p class="hint">Email changes are not supported yet.</p>
                <label for="phone">Phone number</label>
                <input id="phone" type="tel" required autocomplete="tel">
                <label for="mailingAddress">Mailing address</label>
                <textarea id="mailingAddress" rows="3" required autocomplete="street-address"></textarea>
                <label class="profile-toggle">
                  <input id="allowNegativeWithdraw" type="checkbox">
                  <span>Allow negative envelope balance on withdrawal</span>
                </label>
                <p id="profileError" class="error" hidden></p>
                <p id="profileSuccess" class="notice success" hidden></p>
                <p class="actions">
                  <button type="button" id="btnSaveProfile">Save profile</button>
                  <a href="/household/invite">Invite household member</a>
                  <a href="/">Back to home</a>
                </p>
                <section id="unlockDevices" class="device-section">
                  <h3>Unlock devices</h3>
                  <p class="hint">Register this browser to unlock with Touch ID, Face ID, or Windows Hello. Anyone who can unlock this hardware and is signed in as you can read the ledger. Remove a lost device from this list.</p>
                  <p id="deviceSetupHint" class="hint" hidden>Complete key setup before registering a device.</p>
                  <ul id="deviceList" class="device-list"></ul>
                  <p id="deviceEmpty" class="hint" hidden>No devices registered yet.</p>
                  <p id="deviceLockedHint" class="hint" hidden>Unlock your ledger to add this device.</p>
                  <p id="deviceUnsupportedHint" class="hint" hidden>This browser cannot register a device unlock. Use your passphrase here.</p>
                  <p id="deviceError" class="error" hidden></p>
                  <p id="deviceSuccess" class="notice success" hidden></p>
                  <p class="actions">
                    <button type="button" id="btnAddDevice" hidden>Add this device</button>
                  </p>
                </section>
                """ + UserNav.keyPageScript("acruet-profile.js");
    }

    private static String profileCss() {
        return """
                input:disabled, textarea:disabled {
                  opacity: 0.7;
                  cursor: not-allowed;
                }
                .profile-toggle {
                  display: flex;
                  align-items: flex-start;
                  gap: 0.65rem;
                  margin-top: 1.25rem;
                  font-weight: 600;
                  cursor: pointer;
                }
                .profile-toggle input {
                  width: auto;
                  margin-top: 0.2rem;
                }
                .notice.success {
                  border-left: 3px solid #34d399;
                }
                .device-section {
                  margin-top: 2.25rem;
                  padding-top: 1.5rem;
                  border-top: 1px solid rgba(148, 163, 184, 0.2);
                }
                .device-list {
                  list-style: none;
                  margin: 1rem 0 0;
                  padding: 0;
                }
                .device-row {
                  display: flex;
                  justify-content: space-between;
                  align-items: center;
                  gap: 0.75rem;
                  padding: 0.75rem 0;
                  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
                }
                .device-row .device-meta {
                  min-width: 0;
                  flex: 1;
                }
                .device-row .device-name {
                  font: inherit;
                  font-weight: 600;
                  color: inherit;
                  background: transparent;
                  border: none;
                  padding: 0.1rem 0.35rem;
                  margin: 0;
                  border-radius: 4px;
                  cursor: pointer;
                  text-align: left;
                }
                .device-row .device-name:hover { background: rgba(148, 163, 184, 0.15); }
                .device-row .device-name-input {
                  font: inherit;
                  font-weight: 600;
                  width: min(100%, 16rem);
                  margin: 0;
                }
                .device-row .added {
                  color: var(--muted);
                  font-size: 0.85rem;
                  margin: 0.2rem 0 0;
                }
                .device-row .device-actions button {
                  margin-top: 0;
                  padding: 0.35rem 0.75rem;
                  font-size: 0.85rem;
                  background: #475569;
                  color: var(--text);
                }
                """;
    }

    public static class ProfileUpdateRequest {
        public String displayName;
        public String phone;
        public String mailingAddress;
        public boolean allowNegativeWithdraw;
    }
}
