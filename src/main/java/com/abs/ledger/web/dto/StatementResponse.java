package com.abs.ledger.web.dto;

import com.abs.ledger.domain.LedgerEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An account's entries in posting order, each with the running balance after it,
 * and the closing balance. The running balance is derived here from the entries,
 * consistent with the balance endpoint.
 */
public record StatementResponse(
        UUID accountId,
        List<Entry> entries,
        BigDecimal closingBalance
) {
    public record Entry(
            UUID entryId,
            UUID transactionId,
            BigDecimal amount,
            BigDecimal runningBalance,
            Instant createdAt
    ) {
    }

    public static StatementResponse of(UUID accountId, List<LedgerEntry> ledgerEntries) {
        List<Entry> rows = new ArrayList<>(ledgerEntries.size());
        BigDecimal running = BigDecimal.ZERO.setScale(2);
        for (LedgerEntry e : ledgerEntries) {
            running = running.add(e.getAmount());
            rows.add(new Entry(e.getId(), e.getTransaction().getId(), e.getAmount(),
                    running, e.getCreatedAt()));
        }
        return new StatementResponse(accountId, rows, running);
    }
}
