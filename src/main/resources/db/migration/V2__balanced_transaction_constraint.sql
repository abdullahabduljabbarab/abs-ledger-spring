-- Enforce the zero-sum invariant in the database itself.
--
-- A foreign key proves an entry points at a real transaction and account, but it
-- says nothing about whether the entries of a transaction sum to zero. That is a
-- cross-row invariant, so it is enforced with a CONSTRAINT TRIGGER deferred to
-- commit: by the time the check runs, both sides of the transaction are present,
-- and a transaction whose entries do not sum to zero cannot be posted, even by a
-- direct SQL insert that bypasses the application.

CREATE OR REPLACE FUNCTION ledger_transaction_balances()
    RETURNS TRIGGER AS $$
DECLARE
    entry_sum NUMERIC(19, 2);
BEGIN
    SELECT COALESCE(SUM(amount), 0) INTO entry_sum
    FROM ledger_entries
    WHERE transaction_id = NEW.transaction_id;

    IF entry_sum <> 0 THEN
        RAISE EXCEPTION 'transaction % does not balance: entries sum to %',
            NEW.transaction_id, entry_sum;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_ledger_transaction_balances
    AFTER INSERT ON ledger_entries
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION ledger_transaction_balances();
