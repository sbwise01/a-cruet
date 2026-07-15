package com.bradandmarsha.acruet.approval;

/**
 * Admin actions recorded in Postgres audit log.
 */
public enum ApprovalAction {
    APPROVE_SIGNUP,
    REJECT_SIGNUP;

    public String dbValue() {
        return name().toLowerCase();
    }
}
