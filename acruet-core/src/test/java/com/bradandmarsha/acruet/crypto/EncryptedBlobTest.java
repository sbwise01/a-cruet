package com.bradandmarsha.acruet.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncryptedBlobTest {

    @Test
    void acceptsValidBlob() {
        byte[] bytes = new byte[EncryptedBlob.IV_BYTES + EncryptedBlob.MIN_CIPHERTEXT_BYTES];
        assertTrue(EncryptedBlob.validationError(Base64.getEncoder().encodeToString(bytes)).isEmpty());
    }

    @Test
    void rejectsShortBlob() {
        byte[] bytes = new byte[EncryptedBlob.IV_BYTES + EncryptedBlob.MIN_CIPHERTEXT_BYTES - 1];
        assertFalse(EncryptedBlob.validationError(Base64.getEncoder().encodeToString(bytes)).isEmpty());
    }

    @Test
    void rejectsInvalidBase64() {
        assertFalse(EncryptedBlob.validationError("not!!!base64").isEmpty());
    }
}
