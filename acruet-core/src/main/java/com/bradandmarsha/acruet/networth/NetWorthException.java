package com.bradandmarsha.acruet.networth;

/**
 * Net-worth operation failures surfaced to REST handlers.
 */
public final class NetWorthException extends RuntimeException {

    public NetWorthException(String message) {
        super(message);
    }

    public NetWorthException(String message, Throwable cause) {
        super(message, cause);
    }
}
