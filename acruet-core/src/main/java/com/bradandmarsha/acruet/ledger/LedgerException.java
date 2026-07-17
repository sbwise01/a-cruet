package com.bradandmarsha.acruet.ledger;

/**
 * Ledger operation failures surfaced to REST handlers.
 */
public final class LedgerException extends RuntimeException {

    public LedgerException(String message) {
        super(message);
    }

    public LedgerException(String message, Throwable cause) {
        super(message, cause);
    }
}
