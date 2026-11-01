package com.bradandmarsha.acruet.keys;

import java.util.Base64;
import java.util.Optional;

/**
 * Client-submitted device wrap of the household DEK (PRF-derived KEK, AES-KW).
 */
public final class DeviceWrapPayload {

    public static final String WRAP_ALGORITHM = WrappedDekPayload.WRAP_ALGORITHM;
    public static final int MIN_WRAPPED_DEK_BYTES = WrappedDekPayload.MIN_WRAPPED_DEK_BYTES;
    public static final int MIN_CREDENTIAL_ID_BYTES = 16;
    public static final int MAX_DISPLAY_NAME_LENGTH = 80;
    public static final String DEFAULT_DISPLAY_NAME = "This device";

    private final String wrapAlgorithm;
    private final String wrappedDekBase64;
    private final String credentialIdBase64;
    private final String displayName;

    public DeviceWrapPayload(
            String wrapAlgorithm,
            String wrappedDekBase64,
            String credentialIdBase64,
            String displayName) {
        this.wrapAlgorithm = wrapAlgorithm;
        this.wrappedDekBase64 = wrappedDekBase64;
        this.credentialIdBase64 = credentialIdBase64;
        this.displayName = displayName;
    }

    public Optional<String> validationError() {
        if (!WRAP_ALGORITHM.equals(wrapAlgorithm)) {
            return Optional.of("Unsupported wrap algorithm.");
        }
        byte[] wrappedDek = decodeFlexible(wrappedDekBase64);
        if (wrappedDek == null || wrappedDek.length < MIN_WRAPPED_DEK_BYTES) {
            return Optional.of("Wrapped DEK is invalid.");
        }
        byte[] credentialId = decodeFlexible(credentialIdBase64);
        if (credentialId == null || credentialId.length < MIN_CREDENTIAL_ID_BYTES) {
            return Optional.of("Credential id is invalid.");
        }
        return Optional.empty();
    }

    public byte[] wrappedDekBytes() {
        return decodeFlexible(wrappedDekBase64);
    }

    public byte[] credentialIdBytes() {
        return decodeFlexible(credentialIdBase64);
    }

    public String wrapAlgorithm() {
        return wrapAlgorithm;
    }

    public String sanitizedDisplayName() {
        if (displayName == null || displayName.isBlank()) {
            return DEFAULT_DISPLAY_NAME;
        }
        String trimmed = displayName.strip();
        StringBuilder cleaned = new StringBuilder(trimmed.length());
        for (int index = 0; index < trimmed.length(); index += 1) {
            char character = trimmed.charAt(index);
            if (!Character.isISOControl(character)) {
                cleaned.append(character);
            }
        }
        String value = cleaned.toString().strip();
        if (value.isEmpty()) {
            return DEFAULT_DISPLAY_NAME;
        }
        if (value.length() > MAX_DISPLAY_NAME_LENGTH) {
            return value.substring(0, MAX_DISPLAY_NAME_LENGTH);
        }
        return value;
    }

    static byte[] decodeFlexible(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException ignored) {
            try {
                return Base64.getDecoder().decode(value);
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
    }
}
