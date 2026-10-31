package com.bradandmarsha.acruet.position;

/**
 * Snapshot item lifecycle.
 */
public enum PositionStatus {
    ACTIVE,
    ARCHIVED;

    public static PositionStatus fromDb(String value) {
        return PositionStatus.valueOf(value);
    }

    public String dbValue() {
        return name();
    }
}
