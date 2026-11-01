package com.bradandmarsha.acruet.assets;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuarterlyAssetReminderTest {

    @Test
    void marchWindowIsTwentyFifthThroughThirtyFirst() {
        assertFalse(QuarterlyAssetReminder.inWindow(LocalDate.of(2026, 3, 24)));
        assertTrue(QuarterlyAssetReminder.inWindow(LocalDate.of(2026, 3, 25)));
        assertTrue(QuarterlyAssetReminder.inWindow(LocalDate.of(2026, 3, 31)));
        assertEquals("2026-Q1", QuarterlyAssetReminder.quarterId(LocalDate.of(2026, 3, 25)));
        assertFalse(QuarterlyAssetReminder.inWindow(LocalDate.of(2026, 4, 1)));
    }

    @Test
    void juneAndSeptemberWindowsAreTwentyFourthThroughThirtieth() {
        assertFalse(QuarterlyAssetReminder.inWindow(LocalDate.of(2026, 6, 23)));
        assertTrue(QuarterlyAssetReminder.inWindow(LocalDate.of(2026, 6, 24)));
        assertTrue(QuarterlyAssetReminder.inWindow(LocalDate.of(2026, 6, 30)));
        assertEquals("2026-Q2", QuarterlyAssetReminder.quarterId(LocalDate.of(2026, 6, 30)));

        assertFalse(QuarterlyAssetReminder.inWindow(LocalDate.of(2026, 9, 23)));
        assertTrue(QuarterlyAssetReminder.inWindow(LocalDate.of(2026, 9, 24)));
        assertTrue(QuarterlyAssetReminder.inWindow(LocalDate.of(2026, 9, 30)));
        assertEquals("2026-Q3", QuarterlyAssetReminder.quarterId(LocalDate.of(2026, 9, 24)));
    }

    @Test
    void decemberWindowIsTwentyFifthThroughThirtyFirst() {
        assertFalse(QuarterlyAssetReminder.inWindow(LocalDate.of(2026, 12, 24)));
        assertTrue(QuarterlyAssetReminder.inWindow(LocalDate.of(2026, 12, 25)));
        assertTrue(QuarterlyAssetReminder.inWindow(LocalDate.of(2026, 12, 31)));
        assertEquals("2026-Q4", QuarterlyAssetReminder.quarterId(LocalDate.of(2026, 12, 31)));
        assertFalse(QuarterlyAssetReminder.inWindow(LocalDate.of(2027, 1, 1)));
    }

    @Test
    void otherMonthsAreNeverInWindow() {
        assertFalse(QuarterlyAssetReminder.inWindow(LocalDate.of(2026, 1, 31)));
        assertFalse(QuarterlyAssetReminder.inWindow(LocalDate.of(2026, 2, 28)));
        assertNull(QuarterlyAssetReminder.quarterId(LocalDate.of(2026, 2, 28)));
    }
}
