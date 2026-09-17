package com.abs.ledger.web.dto;

import com.abs.ledger.domain.Transaction;
import com.abs.ledger.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        String idempotencyKey,
        TransactionType type,
        BigDecimal amount,
        String reference,
        Instant createdAt
) {
    public static TransactionResponse from(Transaction txn) {
        return new TransactionResponse(txn.getId(), txn.getIdempotencyKey(), txn.getType(),
                txn.getAmount(), txn.getReference(), txn.getCreatedAt());
    }
}
