package com.bradandmarsha.acruet.keys;

/**
 * Key lifecycle failures surfaced to REST handlers.
 */
public final class KeyServiceException extends RuntimeException {

    public KeyServiceException(String message) {
        super(message);
    }

    public KeyServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
