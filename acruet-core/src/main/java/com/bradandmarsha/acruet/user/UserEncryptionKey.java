package com.bradandmarsha.acruet.user;

import java.time.Instant;
import java.util.UUID;

/**
 * Server-side wrapped DEK metadata. The passphrase-derived KEK never leaves the browser.
 */
public record UserEncryptionKey(
        UUID userId,
        byte[] wrappedDek,
        String wrapAlgorithm,
        String kdfAlgorithm,
        String kdfHash,
        byte[] kdfSalt,
        int kdfIterations,
        byte[] recoveryWrappedDek,
        String recoveryWrapAlgorithm,
        Instant createdAt,
        Instant updatedAt) {

    public boolean recoveryEnrolled() {
        return recoveryWrappedDek != null
                && recoveryWrappedDek.length > 0
                && recoveryWrapAlgorithm != null
                && !recoveryWrapAlgorithm.isBlank();
    }
}
