package com.bradandmarsha.acruet.ui;

import com.bradandmarsha.acruet.config.MediaSettings;

/**
 * User-facing HTML shell with homelab index tile branding and README footer.
 *
 * <p>Tile image path and description match {@code ingress-user.yaml} annotations for
 * wise-home-index discovery. The media host comes from {@link MediaSettings}.</p>
 */
public final class UserPageLayout {

    public static final String APP_NAME = "a-cruet";
    public static final String TILE_IMAGE_PATH = "/media/acruet.png";
    public static final String TILE_DESCRIPTION = "Envelope-budgeting web application";

    private UserPageLayout() {
    }

    public static String render(String title, String extraCss, String mainHtml) {
        String tileImage = MediaSettings.fromEnvironment().tileImageUrl(TILE_IMAGE_PATH);
        return PageLayout.render(APP_NAME, tileImage, TILE_DESCRIPTION, title, extraCss, mainHtml);
    }

    public static String render(String title, String mainHtml) {
        return render(title, "", mainHtml);
    }
}
