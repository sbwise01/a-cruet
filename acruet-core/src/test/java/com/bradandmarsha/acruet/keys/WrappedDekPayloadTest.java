package com.bradandmarsha.acruet.keys;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WrappedDekPayloadTest {

    @Test
    void acceptsValidPayload() {
        byte[] salt = new byte[16];
        byte[] wrapped = new byte[32];
        WrappedDekPayload payload = new WrappedDekPayload(
                WrappedDekPayload.KDF_ALGORITHM,
                WrappedDekPayload.KDF_HASH,
                Base64.getEncoder().encodeToString(salt),
                WrappedDekPayload.DEFAULT_ITERATIONS,
                WrappedDekPayload.WRAP_ALGORITHM,
                Base64.getEncoder().encodeToString(wrapped));

        assertTrue(payload.validationError().isEmpty());
        assertEquals(16, payload.kdfSaltBytes().length);
        assertEquals(32, payload.wrappedDekBytes().length);
    }

    @Test
    void rejectsLowIterations() {
        WrappedDekPayload payload = new WrappedDekPayload(
                WrappedDekPayload.KDF_ALGORITHM,
                WrappedDekPayload.KDF_HASH,
                Base64.getEncoder().encodeToString(new byte[16]),
                WrappedDekPayload.MIN_ITERATIONS - 1,
                WrappedDekPayload.WRAP_ALGORITHM,
                Base64.getEncoder().encodeToString(new byte[32]));

        assertTrue(payload.validationError().isPresent());
    }

    @Test
    void rejectsUnsupportedAlgorithm() {
        WrappedDekPayload payload = new WrappedDekPayload(
                "bcrypt",
                WrappedDekPayload.KDF_HASH,
                Base64.getEncoder().encodeToString(new byte[16]),
                WrappedDekPayload.DEFAULT_ITERATIONS,
                WrappedDekPayload.WRAP_ALGORITHM,
                Base64.getEncoder().encodeToString(new byte[32]));

        assertTrue(payload.validationError().isPresent());
    }
}
