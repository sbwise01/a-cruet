package com.bradandmarsha.acruet.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetViewsTest {

    @Test
    void historicalPageIsAStandaloneForm() {
        String html = AssetViews.historicalNetWorthHtml("", "/lock.png");
        assertTrue(html.contains("id=\"historicalNetWorthRoot\""));
        assertTrue(html.contains("Add historical total"));
        assertTrue(html.contains("id=\"btnAddHistoricalNetWorth\""));
        assertTrue(html.contains("href=\"/assets\""));
        assertTrue(html.contains("href=\"/reports/net-worth\""));
        assertTrue(html.contains("acruet-net-worth-historical.js"));
        assertFalse(html.contains("id=\"reportNetWorthPanel\""));
        assertFalse(html.contains("id=\"bundleSelect\""));
    }
}
