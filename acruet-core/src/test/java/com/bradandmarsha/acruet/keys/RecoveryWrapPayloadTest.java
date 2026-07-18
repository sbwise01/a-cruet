package com.bradandmarsha.acruet.keys;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryWrapPayloadTest {

    @Test
    void acceptsValidRecoveryWrap() {
        byte[] wrapped = new byte[32];
        for (int index = 0; index < wrapped.length; index += 1) {
            wrapped[index] = (byte) index;
        }
        RecoveryWrapPayload payload = new RecoveryWrapPayload(
                RecoveryWrapPayload.WRAP_ALGORITHM, Base64.getEncoder().encodeToString(wrapped));
        assertTrue(payload.validationError().isEmpty());
    }

    @Test
    void rejectsMissingWrappedDek() {
        RecoveryWrapPayload payload = new RecoveryWrapPayload(RecoveryWrapPayload.WRAP_ALGORITHM, "");
        assertFalse(payload.validationError().isEmpty());
    }
}
