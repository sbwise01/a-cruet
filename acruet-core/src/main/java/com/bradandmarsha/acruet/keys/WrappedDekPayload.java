package com.bradandmarsha.acruet.keys;

import java.util.Base64;
import java.util.Optional;

/**
 * Validates client-submitted wrapped DEK payloads before persistence.
 */
public final class WrappedDekPayload {

    public static final String KDF_ALGORITHM = "PBKDF2";
    public static final String KDF_HASH = "SHA-256";
    public static final String WRAP_ALGORITHM = "AES-KW";
    public static final int MIN_ITERATIONS = 100_000;
    public static final int DEFAULT_ITERATIONS = 600_000;
    public static final int MIN_SALT_BYTES = 16;
    public static final int MIN_WRAPPED_DEK_BYTES = 16;

    private final String kdfAlgorithm;
    private final String kdfHash;
    private final String kdfSaltBase64;
    private final int kdfIterations;
    private final String wrapAlgorithm;
    private final String wrappedDekBase64;

    public WrappedDekPayload(
            String kdfAlgorithm,
            String kdfHash,
            String kdfSaltBase64,
            int kdfIterations,
            String wrapAlgorithm,
            String wrappedDekBase64) {
        this.kdfAlgorithm = kdfAlgorithm;
        this.kdfHash = kdfHash;
        this.kdfSaltBase64 = kdfSaltBase64;
        this.kdfIterations = kdfIterations;
        this.wrapAlgorithm = wrapAlgorithm;
        this.wrappedDekBase64 = wrappedDekBase64;
    }

    public Optional<String> validationError() {
        if (!KDF_ALGORITHM.equals(kdfAlgorithm)) {
            return Optional.of("Unsupported KDF algorithm.");
        }
        if (!KDF_HASH.equals(kdfHash)) {
            return Optional.of("Unsupported KDF hash.");
        }
        if (!WRAP_ALGORITHM.equals(wrapAlgorithm)) {
            return Optional.of("Unsupported wrap algorithm.");
        }
        if (kdfIterations < MIN_ITERATIONS) {
            return Optional.of("KDF iterations are too low.");
        }
        if (kdfSaltBase64 == null || kdfSaltBase64.isBlank()) {
            return Optional.of("KDF salt is required.");
        }
        if (wrappedDekBase64 == null || wrappedDekBase64.isBlank()) {
            return Optional.of("Wrapped DEK is required.");
        }
        byte[] salt = decodeBase64(kdfSaltBase64);
        if (salt == null || salt.length < MIN_SALT_BYTES) {
            return Optional.of("KDF salt is invalid.");
        }
        byte[] wrappedDek = decodeBase64(wrappedDekBase64);
        if (wrappedDek == null || wrappedDek.length < MIN_WRAPPED_DEK_BYTES) {
            return Optional.of("Wrapped DEK is invalid.");
        }
        return Optional.empty();
    }

    public byte[] kdfSaltBytes() {
        return decodeBase64(kdfSaltBase64);
    }

    public byte[] wrappedDekBytes() {
        return decodeBase64(wrappedDekBase64);
    }

    public String kdfAlgorithm() {
        return kdfAlgorithm;
    }

    public String kdfHash() {
        return kdfHash;
    }

    public int kdfIterations() {
        return kdfIterations;
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
