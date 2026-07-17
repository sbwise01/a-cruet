package com.bradandmarsha.acruet.ledger;

/**
 * Ledger account lifecycle (Phase 8).
 */
public enum LedgerAccountStatus {
    ACTIVE,
    ARCHIVED;

    public static LedgerAccountStatus fromDb(String value) {
        return LedgerAccountStatus.valueOf(value);
    }

    public String dbValue() {
        return name();
    }
}
