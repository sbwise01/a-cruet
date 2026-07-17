package com.bradandmarsha.acruet.ledger;

/**
 * Append-only ledger transaction types (Phase 8).
 */
public enum TransactionType {
    DEPOSIT,
    WITHDRAW,
    TRANSFER,
    ADJUSTMENT;

    public static TransactionType fromDb(String value) {
        return TransactionType.valueOf(value);
    }

    public String dbValue() {
        return name();
    }
}
