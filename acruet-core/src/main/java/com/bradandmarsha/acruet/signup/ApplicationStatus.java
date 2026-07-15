package com.bradandmarsha.acruet.signup;

/**
 * Signup application lifecycle states (Phase 5–6).
 */
public enum ApplicationStatus {
    PENDING_VERIFICATION,
    PENDING_APPROVAL,
    REJECTED,
    BLOCKED,
    APPROVED;

    public String dbValue() {
        return name().toLowerCase();
    }

    public static ApplicationStatus fromDb(String value) {
        return ApplicationStatus.valueOf(value.toUpperCase());
    }
}
