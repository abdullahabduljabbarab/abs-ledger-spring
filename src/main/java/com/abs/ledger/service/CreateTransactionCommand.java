package com.abs.ledger.service;

import com.abs.ledger.domain.TransactionType;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * A validated request to post a transaction, decoupled from the web DTO. The
 * amount is expected already normalised to two decimal places by the caller.
 */
public record CreateTransactionCommand(
        String idempotencyKey,
        TransactionType type,
        BigDecimal amount,
        UUID accountId,
        UUID fromAccountId,
        UUID toAccountId,
        String reference
) {
}
