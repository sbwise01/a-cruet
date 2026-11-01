package com.bradandmarsha.acruet.ui;

import com.bradandmarsha.acruet.auth.OidcUser;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminPageLayoutTest {

    private static final OidcUser ADMIN = new OidcUser(
            "kc-admin",
            "swise",
            "admin@example.com",
            "Stephen",
            "Wise",
            Set.of("a-cruet-admin"));

    @Test
    void innerPageIncludesAvatarAndHeaderHome() {
        String html = AdminPageLayout.renderAuthenticated(
                "Users",
                "",
                "<h2>Users</h2>",
                ADMIN,
                true);

        assertTrue(html.contains("id=\"adminNav\""));
        assertTrue(html.contains("class=\"avatar-btn\""));
        assertTrue(html.contains(">SW<"));
        assertTrue(html.contains("data-name=\"Stephen Wise\""));
        assertTrue(html.contains("data-email=\"admin@example.com\""));
        assertTrue(html.contains("acruet-admin-nav.js"));
        assertTrue(html.contains("class=\"header-home\""));
        assertTrue(html.contains("href=\"/\""));
        assertTrue(html.contains(".avatar-btn"));
    }

    @Test
    void dashboardOmitsHeaderHomeLink() {
        String html = AdminPageLayout.renderAuthenticated(
                AdminPageLayout.APP_NAME,
                "",
                "<ul></ul>",
                ADMIN,
                false);

        assertTrue(html.contains("id=\"adminNav\""));
        assertFalse(html.contains("class=\"header-home\""));
        assertTrue(html.contains("https://home.bradandmarsha.com/"));
        assertTrue(html.contains("wise-k8s home"));
    }
}
