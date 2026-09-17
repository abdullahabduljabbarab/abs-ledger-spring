# Engineering Report

## What this is

A double-entry ledger core on Java 21 and Spring Boot: accounts, transactions
posted as balanced entry pairs, and balances derived from those entries. It is a
focused re-implementation of the core of the ABS ledger (a Python service) on the
stack a bank actually runs, kept deliberately small so the engineering that
matters is visible rather than buried.

## The core idea

One rule holds the design together: the entries of a transaction always sum to
zero. Deposits and withdrawals balance against an internal External Clearing
account; transfers balance between two customer accounts. Because balances are the
sum of entries and are never stored, there is no separate number that can drift
out of agreement with the history.

## Correctness in two places

The invariants are enforced in the service and, independently, in the database:

- **Positive amounts**: Bean Validation on the request, and `CHECK (amount > 0)`.
- **Balanced postings**: the service builds a matched entry pair and asserts it
  sums to zero before commit; the schema's foreign keys ensure every entry points
  at a real transaction and account.
- **Idempotency**: a `UNIQUE` idempotency key, with a stored `request_hash` so a
  key reused with different parameters is a 409 rather than a silently wrong
  reply. A concurrent duplicate that loses the unique-constraint race is caught
  and returned as the same replay, so racing identical requests still post once.
- **Immutability**: no setter, no mutating endpoint, and a trigger that rejects
  any UPDATE or DELETE on `ledger_entries`, so history cannot be rewritten even
  by direct SQL.

Hibernate runs in `validate` mode against the Flyway-owned schema, so a drift
between the mapping and the migration fails at startup rather than in production.

## Concurrency

The interesting failure a ledger must survive is two operations racing on the
same account. Before a balance check the affected accounts are locked with a
pessimistic write lock held to commit, so two concurrent withdrawals cannot both
read a sufficient balance and overspend. Transfers lock both accounts in ascending
id order, so opposing transfers between the same pair take their locks in the same
order and cannot deadlock. The `concurrent_transfers_keep_the_books_balanced` test
fires a hundred parallel transfers at one pair and asserts the books still balance
and the global entry sum is zero: that single test is the pitch in code.

## Money

Every amount is a `BigDecimal` mapped to `numeric(19,2)`, never a floating-point
`double`, and is normalised to two decimal places on the way in so "10" and
"10.00" are the same money and produce the same idempotent request hash.

## Testing

The tests run against a real PostgreSQL in a throwaway container (Testcontainers),
not an in-memory substitute, so they exercise Flyway, the constraints, the trigger
and real row locking, exactly as a deployment would. CI runs the full suite on
Java 21 on every push.

## Relationship to ABS

The tamper-evident hash chain, the transactional outbox and event publishing, the
authentication layer and the cloud deployment are deliberately not here; they
belong to the full ABS ledger and platform and are not repeated. This repository
is the ledger's correctness core, expressed in Spring.

- Original ledger: https://github.com/abdullahabduljabbarab/ledger-api
- The full platform: https://github.com/abdullahabduljabbarab/abs-financial-systems

## Honest limitations

- No authentication (a scope decision; it is meant to run behind the platform
  boundary).
- No rate limiting.
- The statement endpoint is not paginated.
- The service is not separately deployed or load-tested; the measured performance
  story for this design lives in the ABS ledger's SLO document.
