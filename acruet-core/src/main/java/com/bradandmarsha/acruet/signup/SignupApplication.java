package com.bradandmarsha.acruet.signup;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Plaintext signup metadata stored in Postgres (PRODUCT.md Section 1).
 */
public record SignupApplication(
        UUID id,
        String email,
        String fullName,
        String reason,
        String phone,
        String mailingAddress,
        ApplicationStatus status,
        int rejectionCount,
        Optional<Instant> lastRejectedAt,
        Instant createdAt) {
}
