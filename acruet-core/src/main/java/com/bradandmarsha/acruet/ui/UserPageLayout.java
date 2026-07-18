package com.bradandmarsha.acruet.ui;

import com.bradandmarsha.acruet.auth.OidcUser;
import com.bradandmarsha.acruet.config.MediaSettings;
import com.bradandmarsha.acruet.user.AcruetUser;

/**
 * User-facing HTML shell with homelab index tile branding and README footer.
 *
 * <p>Tile image path and description match {@code ingress-user.yaml} annotations for
 * wise-home-index discovery. The media host comes from {@link MediaSettings}.</p>
 */
public final class UserPageLayout {

    public static final String APP_NAME = "a-cruet";
    public static final String TILE_IMAGE_PATH = "/media/acruet-bw.jpg";
    public static final String LOCK_IMAGE_PATH = "/media/lock.png";
    public static final String TILE_DESCRIPTION = "Envelope-budgeting web application";

    private UserPageLayout() {
    }

    /** Anonymous marketing landing at {@code /} (Phase 9 items 1 and 3). */
    public static String renderPublicMarketing(String mainHtml) {
        String tileImage = MediaSettings.fromEnvironment().tileImageUrl(TILE_IMAGE_PATH);
        return PageLayout.render(
                APP_NAME,
                tileImage,
                TILE_DESCRIPTION,
                APP_NAME,
                PageStyles.navCss() + PageStyles.marketingCss(),
                UserNav.publicNavHtml(),
                mainHtml);
    }

    /** Authenticated pages with avatar menu (Phase 9 item 4). */
    public static String renderAuthenticated(String title, String extraCss, String mainHtml, AuthNavContext nav) {
        String tileImage = MediaSettings.fromEnvironment().tileImageUrl(TILE_IMAGE_PATH);
        return PageLayout.render(
                APP_NAME,
                tileImage,
                TILE_DESCRIPTION,
                title,
                PageStyles.navCss() + (extraCss == null ? "" : extraCss),
                UserNav.authNavHtml(nav),
                mainHtml + UserNav.authNavScripts());
    }

    /** Signup and other flows without top nav chrome. */
    public static String render(String title, String extraCss, String mainHtml) {
        String tileImage = MediaSettings.fromEnvironment().tileImageUrl(TILE_IMAGE_PATH);
        return PageLayout.render(APP_NAME, tileImage, TILE_DESCRIPTION, title, extraCss, mainHtml);
    }

    public static String render(String title, String mainHtml) {
        return render(title, "", mainHtml);
    }

    public static AuthNavContext navContext(OidcUser oidcUser, AcruetUser acruetUser, boolean inlineUnlockHome) {
        boolean linked = acruetUser != null;
        return new AuthNavContext(
                linked ? UserInitials.from(acruetUser, oidcUser) : UserInitials.from(oidcUser),
                UserInitials.displayName(acruetUser, oidcUser),
                linked ? acruetUser.email() : oidcUser.email(),
                linked,
                linked && acruetUser.keySetupComplete(),
                inlineUnlockHome);
    }

    public static String lockImageUrl() {
        return MediaSettings.fromEnvironment().tileImageUrl(LOCK_IMAGE_PATH);
    }
}
