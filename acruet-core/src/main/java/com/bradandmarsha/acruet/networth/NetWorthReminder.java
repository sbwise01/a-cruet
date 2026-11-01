package com.bradandmarsha.acruet.networth;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Staleness and snooze math for the home net-worth reminder (PRODUCT.md §11).
 *
 * <p>Runtime display is client-side; this class locks the calendar rules for tests.
 * Latest {@code as_of_date} plus one calendar month on or before today is stale.
 * An empty series is stale. {@code Not now} hides the dialog for seven local days.</p>
 */
public final class NetWorthReminder {

    private NetWorthReminder() {
    }

    public static boolean isStale(LocalDate asOfDate, LocalDate today) {
        Objects.requireNonNull(today, "today");
        if (asOfDate == null) {
            return true;
        }
        return !asOfDate.plusMonths(1).isAfter(today);
    }

    public static LocalDate snoozeUntil(LocalDate today) {
        Objects.requireNonNull(today, "today");
        return today.plusDays(7);
    }

    public static boolean isSnoozed(LocalDate notNowUntil, LocalDate today) {
        Objects.requireNonNull(today, "today");
        return notNowUntil != null && today.isBefore(notNowUntil);
    }
}
