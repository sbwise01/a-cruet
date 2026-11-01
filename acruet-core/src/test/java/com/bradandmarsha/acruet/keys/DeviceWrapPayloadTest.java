package com.bradandmarsha.acruet.keys;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceWrapPayloadTest {

    @Test
    void acceptsValidDeviceWrap() {
        DeviceWrapPayload payload = new DeviceWrapPayload(
                DeviceWrapPayload.WRAP_ALGORITHM,
                Base64.getEncoder().encodeToString(new byte[32]),
                Base64.getEncoder().encodeToString(new byte[32]),
                "Kitchen iPad");
        assertTrue(payload.validationError().isEmpty());
        assertEquals("Kitchen iPad", payload.sanitizedDisplayName());
    }

    @Test
    void defaultsBlankDisplayName() {
        DeviceWrapPayload payload = new DeviceWrapPayload(
                DeviceWrapPayload.WRAP_ALGORITHM,
                Base64.getEncoder().encodeToString(new byte[32]),
                Base64.getEncoder().encodeToString(new byte[32]),
                "  ");
        assertEquals(DeviceWrapPayload.DEFAULT_DISPLAY_NAME, payload.sanitizedDisplayName());
    }
}
