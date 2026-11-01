package com.bradandmarsha.acruet.assets;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Calendar window for the quarterly assets/liabilities reminder (PRODUCT.md §10).
 *
 * <p>The last seven calendar days of March, June, September, and December, using
 * the viewer's local date. Runtime display is client-side; this class locks the
 * date math for tests.</p>
 */
public final class QuarterlyAssetReminder {

    private QuarterlyAssetReminder() {
    }

    public static boolean inWindow(LocalDate date) {
        return quarterId(date) != null && inLastSevenDaysOfMonth(date);
    }

    /**
     * {@code YYYY-Qn} when {@code date} falls in a quarter-ending month; otherwise
     * {@code null}. Callers who need the active reminder quarter should also check
     * {@link #inWindow(LocalDate)}.
     */
    public static String quarterId(LocalDate date) {
        Objects.requireNonNull(date, "date");
        return switch (date.getMonthValue()) {
            case 3 -> date.getYear() + "-Q1";
            case 6 -> date.getYear() + "-Q2";
            case 9 -> date.getYear() + "-Q3";
            case 12 -> date.getYear() + "-Q4";
            default -> null;
        };
    }

    static boolean inLastSevenDaysOfMonth(LocalDate date) {
        int last = date.lengthOfMonth();
        int day = date.getDayOfMonth();
        return day >= last - 6 && day <= last;
    }
}
