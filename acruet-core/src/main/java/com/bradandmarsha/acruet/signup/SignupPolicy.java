package com.bradandmarsha.acruet.signup;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Re-apply rules from PRODUCT.md Section 6 (#45).
 */
public final class SignupPolicy {

    public static final int COOLDOWN_DAYS = 7;
    public static final int MAX_REJECTIONS = 2;

    public enum Decision {
        ALLOW,
        IN_PROGRESS,
        COOLDOWN,
        BLOCKED
    }

    private SignupPolicy() {
    }

    public static Decision evaluate(Optional<SignupApplication> latest, Instant now) {
        if (latest.isEmpty()) {
            return Decision.ALLOW;
        }
        SignupApplication application = latest.get();
        if (application.status() == ApplicationStatus.PENDING_VERIFICATION
                || application.status() == ApplicationStatus.PENDING_APPROVAL) {
            return Decision.IN_PROGRESS;
        }
        if (application.status() == ApplicationStatus.BLOCKED
                || application.rejectionCount() >= MAX_REJECTIONS) {
            return Decision.BLOCKED;
        }
        if (application.status() == ApplicationStatus.REJECTED
                && application.lastRejectedAt().isPresent()) {
            Instant cooldownEnds = application.lastRejectedAt().get().plus(COOLDOWN_DAYS, ChronoUnit.DAYS);
            if (now.isBefore(cooldownEnds)) {
                return Decision.COOLDOWN;
            }
        }
        return Decision.ALLOW;
    }
}
