package com.bradandmarsha.acruet.ledger;

/**
 * Envelope bundle lifecycle.
 */
public enum EnvelopeBundleStatus {
    ACTIVE,
    ARCHIVED;

    public static EnvelopeBundleStatus fromDb(String value) {
        return EnvelopeBundleStatus.valueOf(value);
    }

    public String dbValue() {
        return name();
    }
}
