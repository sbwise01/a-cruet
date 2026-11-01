package com.bradandmarsha.acruet.keys;

import java.time.Instant;
import java.util.UUID;

/**
 * One registered platform authenticator wrap of the household DEK.
 */
public record UserUnlockDevice(
        UUID id,
        UUID userId,
        byte[] credentialId,
        byte[] wrappedDek,
        String wrapAlgorithm,
        String displayName,
        Instant createdAt,
        Instant lastUsedAt) {
}
