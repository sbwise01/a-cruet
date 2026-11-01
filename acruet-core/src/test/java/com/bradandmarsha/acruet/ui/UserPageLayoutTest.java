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
        assertTrue(html.contains("See current net worth"));
    }
}
