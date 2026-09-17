-- The ledger schema. The invariants the service depends on are declared here, so
-- the database refuses a bad write even if the application logic were wrong:
--   * amounts are strictly positive              (CHECK)
--   * an idempotency key is used at most once     (UNIQUE)
--   * every entry belongs to a real transaction and account (FOREIGN KEY)
--   * posted entries are never changed or removed (append-only trigger)

CREATE TABLE accounts (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(255) NOT NULL UNIQUE,
    is_system  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE transactions (
    id              UUID         PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    type            VARCHAR(255) NOT NULL CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    amount          NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    request_hash    VARCHAR(64)  NOT NULL,
    reference       VARCHAR(255),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE ledger_entries (
    id             UUID           PRIMARY KEY,
    transaction_id UUID           NOT NULL REFERENCES transactions (id),
    account_id     UUID           NOT NULL REFERENCES accounts (id),
    amount         NUMERIC(19, 2) NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX ix_ledger_entries_account ON ledger_entries (account_id);
CREATE INDEX ix_ledger_entries_transaction ON ledger_entries (transaction_id);

-- Append-only enforcement at the database. A posted entry is immutable: once
-- written it can never be updated or deleted, so the ledger history cannot be
-- rewritten even by a direct SQL statement.
CREATE OR REPLACE FUNCTION ledger_entries_append_only()
    RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entries is append-only: % is not permitted', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_entries_append_only
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION ledger_entries_append_only();

-- The External Clearing account is the counterparty for deposits and withdrawals,
-- so those transactions balance to zero like every other. Seeded with a fixed id.
INSERT INTO accounts (id, name, is_system)
VALUES ('00000000-0000-0000-0000-000000000001', 'External Clearing', TRUE);
