package com.abs.ledger.error;

/**
 * The request conflicts with existing state: a duplicate account name, or an
 * idempotency key reused with different parameters. Mapped to HTTP 409.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
