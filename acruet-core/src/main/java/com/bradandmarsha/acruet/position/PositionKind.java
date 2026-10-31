package com.bradandmarsha.acruet.position;

/**
 * Plaintext section for a snapshot item (PRODUCT.md §9).
 */
public enum PositionKind {
    ASSET,
    LIABILITY;

    public static PositionKind fromDb(String value) {
        return PositionKind.valueOf(value);
    }

    public String dbValue() {
        return name();
    }
}
