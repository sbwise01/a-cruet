package com.bradandmarsha.acruet.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportViewsTest {

    @Test
    void hubTilesLinkToDedicatedPagesWithoutBundlePicker() {
        String html = ReportViews.reportsPageHtml(
                "", "/lock.png", ReportViews.ReportPage.HUB, "/tx.png", "/chart.png", "/assets.png");
        assertTrue(html.contains("data-report=\"hub\""));
        assertTrue(html.contains("href=\"/reports/envelope-transactions\""));
        assertTrue(html.contains("href=\"/reports/envelope-balance\""));
        assertTrue(html.contains("href=\"/reports/net-worth\""));
        assertTrue(html.contains("href=\"/reports/value-over-time\""));
        assertTrue(html.contains("Envelope transactions"));
        assertTrue(html.contains("Envelope balance chart"));
        assertTrue(html.contains("Net worth"));
        assertTrue(html.contains("Value over time"));
        assertFalse(html.contains("id=\"bundlePanel\""));
        assertFalse(html.contains("id=\"bundleSelect\""));
        assertFalse(html.contains("Add historical total"));
        assertFalse(html.contains("acruet-net-worth.js"));
        assertFalse(html.contains("acruet-value-over-time.js"));
    }

    @Test
    void envelopePagesIncludeBundlePicker() {
        String transactions = ReportViews.reportsPageHtml(
                "",
                "/lock.png",
                ReportViews.ReportPage.ENVELOPE_TRANSACTIONS,
                "/tx.png",
                "/chart.png",
                "/assets.png");
        assertTrue(transactions.contains("data-report=\"transactions\""));
        assertTrue(transactions.contains("id=\"bundleSelect\""));
        assertTrue(transactions.contains("Envelope transaction report"));
        assertTrue(transactions.contains("href=\"/reports\""));
        assertFalse(transactions.contains("id=\"reportNetWorthPanel\""));

        String chart = ReportViews.reportsPageHtml(
                "",
                "/lock.png",
                ReportViews.ReportPage.ENVELOPE_BALANCE,
                "/tx.png",
                "/chart.png",
                "/assets.png");
        assertTrue(chart.contains("data-report=\"chart\""));
        assertTrue(chart.contains("id=\"bundleSelect\""));
        assertTrue(chart.contains("Envelope balance over time"));
        assertTrue(chart.contains("chart.umd.min.js"));
    }

    @Test
    void netWorthPageHasNoBundlePicker() {
        String html = ReportViews.reportsPageHtml(
                "",
                "/lock.png",
                ReportViews.ReportPage.NET_WORTH,
                "/tx.png",
                "/chart.png",
                "/assets.png");
        assertTrue(html.contains("data-report=\"net-worth\""));
        assertTrue(html.contains("id=\"reportNetWorthPanel\""));
        assertTrue(html.contains("acruet-net-worth.js"));
        assertTrue(html.contains("href=\"/reports\""));
        assertFalse(html.contains("id=\"bundlePanel\""));
        assertFalse(html.contains("id=\"bundleSelect\""));
        assertFalse(html.contains("Add historical total"));
        assertFalse(html.contains("Save net worth"));
    }

    @Test
    void valueOverTimePageHasSeriesPickerWithoutBundlePicker() {
        String html = ReportViews.reportsPageHtml(
                "",
                "/lock.png",
                ReportViews.ReportPage.VALUE_OVER_TIME,
                "/tx.png",
                "/chart.png",
                "/assets.png");
        assertTrue(html.contains("data-report=\"value-over-time\""));
        assertTrue(html.contains("id=\"reportValueOverTimePanel\""));
        assertTrue(html.contains("id=\"valueSeriesSelect\""));
        assertTrue(html.contains("acruet-value-over-time.js"));
        assertTrue(html.contains("chart.umd.min.js"));
        assertTrue(html.contains("href=\"/reports\""));
        assertFalse(html.contains("id=\"bundlePanel\""));
        assertFalse(html.contains("id=\"bundleSelect\""));
        assertFalse(html.contains("acruet-reports.js"));
        assertFalse(html.contains("acruet-net-worth.js"));
    }
}
