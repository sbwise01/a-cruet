package com.bradandmarsha.acruet.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UserPageLayoutTest {

    @Test
    void publicMarketingIncludesHomeIndexLink() {
        String html = UserPageLayout.renderPublicMarketing(MarketingContent.html());
        assertTrue(html.contains("https://home.bradandmarsha.com/"));
        assertTrue(html.contains("wise-k8s home"));
        assertTrue(html.contains("Personal financial management application"));
        assertTrue(html.contains("personal financial management"));
        assertTrue(html.contains("Envelope budgeting"));
        assertTrue(html.contains("Save a net worth history"));
        assertTrue(html.contains("year-end totals"));
        assertTrue(html.contains("keep a net worth history"));
    }

    @Test
    void authenticatedShellLoadsNetWorthReminder() {
        String html = UserPageLayout.renderAuthenticated(
                "Home",
                "",
                "<p>home</p>",
                new AuthNavContext("BW", "Brad", "brad@example.com", true, true, false),
                false);
        assertTrue(html.contains("acruet-net-worth.js"));
        assertTrue(html.contains("acruet-net-worth-reminder.js"));
        assertTrue(html.contains("acruet-asset-reminder.js"));
    }
}
