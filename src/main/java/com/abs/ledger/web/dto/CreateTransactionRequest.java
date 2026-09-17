package com.abs.ledger.web.dto;

import com.abs.ledger.domain.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Money is a {@link BigDecimal} with at most two fraction digits, never a double.
 * Which account fields are required depends on the type, checked in the service:
 * account_id for deposits and withdrawals, from/to for transfers.
 */
public record CreateTransactionRequest(
        @NotBlank @Size(max = 255) String idempotencyKey,
        @NotNull TransactionType type,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        UUID accountId,
        UUID fromAccountId,
        UUID toAccountId,
        @Size(max = 255) String reference
) {
}
