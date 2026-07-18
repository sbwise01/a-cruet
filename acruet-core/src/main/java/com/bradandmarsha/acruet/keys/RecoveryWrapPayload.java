package com.bradandmarsha.acruet.keys;

import java.util.Base64;
import java.util.Optional;

/**
 * Validates client-submitted recovery-wrap payloads (high-entropy recovery secret wrap, Phase 7.1).
 */
public final class RecoveryWrapPayload {

    public static final String WRAP_ALGORITHM = WrappedDekPayload.WRAP_ALGORITHM;
    private static final int MIN_WRAPPED_DEK_BYTES = WrappedDekPayload.MIN_WRAPPED_DEK_BYTES;

    private final String wrapAlgorithm;
    private final String recoveryWrappedDekBase64;

    public RecoveryWrapPayload(String wrapAlgorithm, String recoveryWrappedDekBase64) {
        this.wrapAlgorithm = wrapAlgorithm;
        this.recoveryWrappedDekBase64 = recoveryWrappedDekBase64;
    }

    public Optional<String> validationError() {
        if (!WRAP_ALGORITHM.equals(wrapAlgorithm)) {
            return Optional.of("Unsupported recovery wrap algorithm.");
        }
        if (recoveryWrappedDekBase64 == null || recoveryWrappedDekBase64.isBlank()) {
            return Optional.of("Recovery wrapped DEK is required.");
        }
        byte[] wrappedDek = decodeBase64(recoveryWrappedDekBase64);
        if (wrappedDek == null || wrappedDek.length < MIN_WRAPPED_DEK_BYTES) {
            return Optional.of("Recovery wrapped DEK is invalid.");
        }
        return Optional.empty();
    }

    public byte[] recoveryWrappedDekBytes() {
        return decodeBase64(recoveryWrappedDekBase64);
    }

    public String wrapAlgorithm() {
        return wrapAlgorithm;
    }

    private static byte[] decodeBase64(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
