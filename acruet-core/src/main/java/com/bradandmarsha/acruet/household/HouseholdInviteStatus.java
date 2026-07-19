package com.bradandmarsha.acruet.household;

/**
 * Lifecycle of a household member invite (Phase 12c).
 */
public enum HouseholdInviteStatus {
    PENDING("pending"),
    ACCEPTED("accepted"),
    REVOKED("revoked"),
    EXPIRED("expired");

    private final String dbValue;

    HouseholdInviteStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static HouseholdInviteStatus fromDb(String value) {
        for (HouseholdInviteStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown household invite status: " + value);
    }
}
