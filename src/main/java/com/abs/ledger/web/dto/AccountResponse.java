package com.abs.ledger.web.dto;

import com.abs.ledger.domain.Account;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String name,
        boolean system,
        Instant createdAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getName(),
                account.isSystem(), account.getCreatedAt());
    }
}
