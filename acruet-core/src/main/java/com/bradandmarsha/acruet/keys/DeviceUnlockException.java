package com.bradandmarsha.acruet.keys;

/**
 * Device unlock failures surfaced to REST handlers.
 */
public final class DeviceUnlockException extends RuntimeException {

    public DeviceUnlockException(String message) {
        super(message);
    }

    public DeviceUnlockException(String message, Throwable cause) {
        super(message, cause);
    }
}
