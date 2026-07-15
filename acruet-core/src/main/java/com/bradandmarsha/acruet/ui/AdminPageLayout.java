package com.bradandmarsha.acruet.ui;

import com.bradandmarsha.acruet.config.MediaSettings;

/**
 * Administrator HTML shell with homelab index tile branding and README footer.
 *
 * <p>Tile image path and description match {@code ingress-admin.yaml} annotations for
 * wise-home-index discovery. The media host comes from {@link MediaSettings}.</p>
 */
public final class AdminPageLayout {

    public static final String APP_NAME = "A Cruet Admin";
    public static final String TILE_IMAGE_PATH = "/media/acruet-bw.jpg";
    public static final String TILE_DESCRIPTION = "Administrator console for A Cruet";

    private AdminPageLayout() {
    }

    public static String render(String title, String extraCss, String mainHtml) {
        String tileImage = MediaSettings.fromEnvironment().tileImageUrl(TILE_IMAGE_PATH);
        return PageLayout.render(APP_NAME, tileImage, TILE_DESCRIPTION, title, extraCss, mainHtml);
    }

    public static String render(String title, String mainHtml) {
        return render(title, "", mainHtml);
    }
}
