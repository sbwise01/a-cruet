package com.bradandmarsha.acruet.household;

/**
 * Role of a provisioned user within a household (Phase 12).
 */
public enum HouseholdMemberRole {
    OWNER("owner"),
    MEMBER("member");

    private final String dbValue;

    HouseholdMemberRole(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static HouseholdMemberRole fromDb(String value) {
        for (HouseholdMemberRole role : values()) {
            if (role.dbValue.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown household member role: " + value);
    }
}
