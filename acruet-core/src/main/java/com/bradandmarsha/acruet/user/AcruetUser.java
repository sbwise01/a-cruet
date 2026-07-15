package com.bradandmarsha.acruet.user;

import java.time.Instant;
import java.util.UUID;

/**
 * Provisioned a-cruet user (plaintext operational metadata).
 */
public record AcruetUser(
        UUID id,
        String keycloakUserId,
        String email,
        String displayName,
        UUID signupApplicationId,
        Instant createdAt) {
}
