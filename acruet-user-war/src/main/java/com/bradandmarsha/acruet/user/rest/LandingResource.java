package com.bradandmarsha.acruet.user.rest;

import com.bradandmarsha.acruet.auth.OidcUser;
import com.bradandmarsha.acruet.auth.UserSession;
import com.bradandmarsha.acruet.keys.KeyService;
import com.bradandmarsha.acruet.ui.UserPageLayout;
import com.bradandmarsha.acruet.user.AcruetUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import java.util.Optional;

/**
 * User landing page — public welcome for anonymous visitors; app home when signed in.
 */
@Path("/")
public class LandingResource {

    private final KeyService keyService = new KeyService();

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String index(@Context HttpServletRequest request) {
        Optional<OidcUser> user = UserSession.oidcUser(request);
        return user.map(this::authenticatedPage).orElseGet(this::publicPage);
    }

    private String publicPage() {
        return UserPageLayout.render(
                UserPageLayout.APP_NAME,
                """
                <p>Allocate money across savings goals and track balances over time.</p>
                <p class="hint">Access is by application and admin approval. Existing users can sign in with Keycloak.</p>
                <p class="actions">
                  <a href="/signup">Apply for access</a>
                  <a href="/auth/login">Sign in</a>
                </p>
                """);
    }

    private String authenticatedPage(OidcUser oidcUser) {
        Optional<AcruetUser> acruetUser = keyService.findUser(oidcUser.subject());
        if (acruetUser.isEmpty()) {
            return UserPageLayout.render(
                    UserPageLayout.APP_NAME,
                    """
                    <p>Signed in as <strong>%s</strong>.</p>
                    <p class="hint">No a-cruet account is linked to this login yet.</p>
                    <p><a href="/auth/logout">Sign out</a></p>
                    """.formatted(escape(oidcUser.displayName())));
        }
        AcruetUser user = acruetUser.get();
        if (!user.keySetupComplete()) {
            return UserPageLayout.render(
                    UserPageLayout.APP_NAME,
                    """
                    <p>Signed in as <strong>%s</strong>.</p>
                    <p>Complete encryption key setup before using the ledger.</p>
                    <p class="actions"><a href="/keys/setup">Create encryption key</a></p>
                    <p><a href="/auth/logout">Sign out</a></p>
                    """.formatted(escape(oidcUser.displayName())));
        }
        return UserPageLayout.render(
                UserPageLayout.APP_NAME,
                """
                <p>Signed in as <strong>%s</strong>.</p>
                <p id="unlockStatus" class="hint">Checking encryption key status…</p>
                <p class="actions">
                  <a href="/keys/unlock">Unlock key</a>
                  <a href="/keys/rotate">Rotate key</a>
                  <a href="/ledger">Ledger</a>
                </p>
                <p><a href="/auth/logout" onclick="AcruetCrypto.session.lock()">Sign out</a></p>
                <script src="/static/js/acruet-crypto.js"></script>
                <script>
                  document.addEventListener('DOMContentLoaded', async () => {
                    const status = document.getElementById('unlockStatus');
                    await AcruetCrypto.session.ensureReady();
                    if (AcruetCrypto.session.isUnlocked()) {
                      status.textContent = 'Encryption key unlocked for this session.';
                    } else {
                      status.textContent = 'Unlock your encryption key to access ledger data.';
                    }
                  });
                </script>
                """.formatted(escape(oidcUser.displayName())));
    }

    private static String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
