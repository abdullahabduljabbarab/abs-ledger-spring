package com.abs.ledger.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The three ways money moves through the ledger. A deposit and a withdrawal each
 * balance against the External Clearing account; a transfer balances between two
 * customer accounts. Serialised to lowercase on the wire to match the rest of the
 * ABS platform, stored as the enum name in the database.
 */
public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static TransactionType fromJson(String value) {
        return TransactionType.valueOf(value.trim().toUpperCase());
    }
}
