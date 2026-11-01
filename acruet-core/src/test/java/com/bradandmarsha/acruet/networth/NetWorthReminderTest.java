package com.bradandmarsha.acruet.networth;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetWorthReminderTest {

    @Test
    void emptySeriesIsStale() {
        assertTrue(NetWorthReminder.isStale(null, LocalDate.of(2026, 11, 1)));
    }

    @Test
    void staleWhenAsOfPlusOneMonthIsTodayOrEarlier() {
        assertFalse(NetWorthReminder.isStale(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 11, 1)));
        assertTrue(NetWorthReminder.isStale(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)));
        assertTrue(NetWorthReminder.isStale(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 11, 1)));
    }

    @Test
    void plusMonthClampsEndOfJanuary() {
        assertFalse(NetWorthReminder.isStale(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 27)));
        assertTrue(NetWorthReminder.isStale(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28)));
    }

    @Test
    void notNowSnoozesSevenLocalDays() {
        LocalDate today = LocalDate.of(2026, 11, 1);
        LocalDate until = NetWorthReminder.snoozeUntil(today);
        assertEquals(LocalDate.of(2026, 11, 8), until);
        assertTrue(NetWorthReminder.isSnoozed(until, today));
        assertTrue(NetWorthReminder.isSnoozed(until, LocalDate.of(2026, 11, 7)));
        assertFalse(NetWorthReminder.isSnoozed(until, LocalDate.of(2026, 11, 8)));
        assertFalse(NetWorthReminder.isSnoozed(null, today));
    }
}
