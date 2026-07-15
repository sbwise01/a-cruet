package com.bradandmarsha.acruet.signup;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SignupPolicyTest {

    private static final Instant NOW = Instant.parse("2026-07-14T12:00:00Z");

    @Test
    void allowsWhenNoPriorApplication() {
        assertEquals(SignupPolicy.Decision.ALLOW, SignupPolicy.evaluate(Optional.empty(), NOW));
    }

    @Test
    void blocksInProgressPendingVerification() {
        SignupApplication application = application(ApplicationStatus.PENDING_VERIFICATION, 0, Optional.empty());
        assertEquals(SignupPolicy.Decision.IN_PROGRESS, SignupPolicy.evaluate(Optional.of(application), NOW));
    }

    @Test
    void blocksInProgressPendingApproval() {
        SignupApplication application = application(ApplicationStatus.PENDING_APPROVAL, 0, Optional.empty());
        assertEquals(SignupPolicy.Decision.IN_PROGRESS, SignupPolicy.evaluate(Optional.of(application), NOW));
    }

    @Test
    void enforcesCooldownAfterSingleRejection() {
        SignupApplication application = application(
                ApplicationStatus.REJECTED,
                1,
                Optional.of(NOW.minus(2, ChronoUnit.DAYS)));
        assertEquals(SignupPolicy.Decision.COOLDOWN, SignupPolicy.evaluate(Optional.of(application), NOW));
    }

    @Test
    void allowsReapplyAfterCooldown() {
        SignupApplication application = application(
                ApplicationStatus.REJECTED,
                1,
                Optional.of(NOW.minus(8, ChronoUnit.DAYS)));
        assertEquals(SignupPolicy.Decision.ALLOW, SignupPolicy.evaluate(Optional.of(application), NOW));
    }

    @Test
    void blocksAfterTwoRejections() {
        SignupApplication application = application(
                ApplicationStatus.REJECTED,
                2,
                Optional.of(NOW.minus(30, ChronoUnit.DAYS)));
        assertEquals(SignupPolicy.Decision.BLOCKED, SignupPolicy.evaluate(Optional.of(application), NOW));
    }

    @Test
    void blocksExplicitBlockedStatus() {
        SignupApplication application = application(ApplicationStatus.BLOCKED, 0, Optional.empty());
        assertEquals(SignupPolicy.Decision.BLOCKED, SignupPolicy.evaluate(Optional.of(application), NOW));
    }

    private static SignupApplication application(
            ApplicationStatus status, int rejectionCount, Optional<Instant> lastRejectedAt) {
        return new SignupApplication(
                UUID.randomUUID(),
                "user@example.com",
                "Test User",
                "Reason",
                "555-0100",
                "123 Main St",
                status,
                rejectionCount,
                lastRejectedAt,
                NOW.minus(1, ChronoUnit.DAYS));
    }
}
