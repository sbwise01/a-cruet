package com.bradandmarsha.acruet.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetViewsTest {

    @Test
    void assetsPageHasAddGroupAction() {
        String html = AssetViews.assetsMainHtml("", "/lock.png");
        assertTrue(html.contains("data-action=\"create-group\""));
        assertTrue(html.contains("Add group"));
        assertTrue(html.contains("id=\"assetsTotal\""));
        assertTrue(html.contains("id=\"liabilitiesTotal\""));
        assertTrue(html.contains("id=\"netTotal\""));
        assertTrue(html.contains("id=\"btnSaveNetWorth\""));
        assertTrue(html.contains("acruet-assets.js"));
    }

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
