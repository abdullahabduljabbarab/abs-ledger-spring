package com.abs.ledger.error;

/**
 * The request was well formed but cannot be posted: a missing account reference,
 * a self-transfer, or insufficient balance. Mapped to HTTP 422.
 */
public class UnprocessableException extends RuntimeException {

    public UnprocessableException(String message) {
        super(message);
    }
}
