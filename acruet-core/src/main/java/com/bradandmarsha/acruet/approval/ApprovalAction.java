package com.bradandmarsha.acruet.approval;

/**
 * Admin actions recorded in Postgres audit log.
 */
public enum ApprovalAction {
    APPROVE_SIGNUP,
    REJECT_SIGNUP,
    GRANT_ADMIN,
    REVOKE_ADMIN,
    SUSPEND_USER,
    UNSUSPEND_USER,
    RESET_SIGNIN_PASSWORD,
    OFFBOARD_USER,
    PURGE_USER,
    UNBLOCK_SIGNUP,
    ALERT_LOGIN_ANOMALY;

    public String dbValue() {
        return name().toLowerCase();
    }
}
