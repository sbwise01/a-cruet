package com.bradandmarsha.acruet.crypto;

import java.util.Base64;
import java.util.Optional;

/**
 * Validates client-submitted AES-GCM ciphertext blobs (IV prepended, Phase 8+).
 */
public final class EncryptedBlob {

    public static final int IV_BYTES = 12;
    public static final int MIN_CIPHERTEXT_BYTES = 16;

    private EncryptedBlob() {
    }

    public static Optional<String> validationError(String base64) {
        if (base64 == null || base64.isBlank()) {
            return Optional.of("Encrypted payload is required.");
        }
        byte[] bytes = decodeBase64(base64);
        if (bytes == null) {
            return Optional.of("Encrypted payload is not valid base64.");
        }
        if (bytes.length < IV_BYTES + MIN_CIPHERTEXT_BYTES) {
            return Optional.of("Encrypted payload is too short.");
        }
        return Optional.empty();
    }

    public static byte[] decode(String base64) {
        return decodeBase64(base64);
    }

    public static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static byte[] decodeBase64(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
