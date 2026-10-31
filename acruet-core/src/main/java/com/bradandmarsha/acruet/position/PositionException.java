package com.bradandmarsha.acruet.position;

/**
 * Position operation failures surfaced to REST handlers.
 */
public final class PositionException extends RuntimeException {

    public PositionException(String message) {
        super(message);
    }

    public PositionException(String message, Throwable cause) {
        super(message, cause);
    }
}
