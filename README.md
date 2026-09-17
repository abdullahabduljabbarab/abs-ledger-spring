# abs-ledger-spring

A deliberately small double-entry ledger, built on Java 21 and Spring Boot. It
holds accounts, posts transactions as balanced pairs of entries, and derives
every balance from those entries. The point is not size, it is correctness: the
ledger invariants are enforced twice, once in the service and once in the
database, so a bad write is refused even if the application logic were wrong.

## Why this exists

It is a focused Java/Spring re-implementation of the core of the ABS ledger, the
authoritative money-moving service in a larger distributed financial platform.
That original is a Python service; this one demonstrates the same invariants in
the Spring stack a bank actually runs. The wider system, its payment
orchestration, risk engine, event-driven services and cloud infrastructure, lives
in the ABS repositories and is not repeated here:

- Original ledger: https://github.com/abdullahabduljabbarab/ledger-api
- The full platform: https://github.com/abdullahabduljabbarab/abs-financial-systems

## The invariants

1. **Every transaction balances.** The entries of a single transaction always sum
   to zero. A deposit and a withdrawal each balance against a system *External
   Clearing* account; a transfer balances between two customer accounts.
2. **All or nothing.** A transaction posts completely or not at all. It is one
   database transaction.
3. **Balances are derived.** An account balance is the sum of its entries,
   computed on read, never a stored mutable number that could drift.
4. **Entries are immutable.** A posted entry is never updated or deleted, so
   history cannot be rewritten.
5. **Duplicate requests are idempotent.** A repeated request carrying the same
   idempotency key returns the original transaction instead of posting a second.

## Correctness at the boundary

Each invariant is enforced by the database, not only by Java:

| Invariant | In the service | In PostgreSQL (Flyway migration) |
|-----------|----------------|----------------------------------|
| Positive amounts | Bean Validation on the request | `CHECK (amount > 0)` |
| Balanced transaction | Paired entries, summed and asserted before commit | entries reference a real transaction and account via `FOREIGN KEY` |
| Idempotency | Look up the key, compare the request hash | `UNIQUE` on `idempotency_key`, caught as a race and replayed |
| Immutable entries | No setters, no update endpoint | trigger rejecting any `UPDATE`/`DELETE` on `ledger_entries` |
| Derived balance | `SUM` query, coalesced to zero | balance is never stored |

Money is always a `BigDecimal` mapped to a `NUMERIC(19,2)` column, never a
floating-point `double`.

Concurrency: before a balance check the affected accounts are locked with a
pessimistic row lock, taken in stable id order so two opposing transfers between
the same pair cannot deadlock. That is what lets concurrent transfers run without
losing money or overspending.

## API

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/accounts` | Open an account |
| `POST` | `/transactions` | Post a deposit, withdrawal or transfer |
| `GET`  | `/accounts/{id}/balance` | The account's derived balance |
| `GET`  | `/accounts/{id}/entries` | The account's entries with a running balance |

A newly posted transaction returns `201`; an idempotent replay of an existing one
returns `200`, so a duplicate request is visibly not a second posting. Interactive
API docs are at `/swagger-ui.html`.

Example, open an account and deposit into it:

```bash
curl -sX POST localhost:8080/accounts \
  -H 'Content-Type: application/json' -d '{"name":"alice"}'

curl -sX POST localhost:8080/transactions \
  -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"dep-1","type":"deposit","amount":"100.00","account_id":"<id>"}'

curl -s localhost:8080/accounts/<id>/balance
```

## Run it

One command brings up PostgreSQL and the service (needs only Docker, no local
Java or Maven):

```bash
docker compose up --build
```

The API is then on `http://localhost:8080`. Flyway applies the schema on startup.

For local development with a JDK 21 and Maven installed:

```bash
mvn spring-boot:run   # expects PostgreSQL on localhost:5432 (see docker-compose.yml)
```

## Tests

```bash
mvn verify
```

The tests run against a real PostgreSQL in a throwaway container
([Testcontainers](https://testcontainers.com/)), so they exercise Flyway, the
constraints, the trigger and real row locking, not an in-memory stand-in. Each
test maps to an invariant:

| Test | What it proves |
|------|----------------|
| `deposit_credits_the_account` | derived balance reflects a posting |
| `withdrawal_debits_and_cannot_overspend` | overdraft is refused with `422`, balance unchanged |
| `transfer_moves_money_and_conserves_the_pair` | a transfer conserves the two accounts |
| `duplicate_idempotency_key_replays_with_200_and_applies_once` | idempotent replay, applied once |
| `same_key_different_parameters_is_a_conflict` | key reuse with different params is `409` |
| `concurrent_transfers_keep_the_books_balanced` | 100 parallel transfers, books still balance |
| `every_transaction_balances_to_zero` | no transaction has a non-zero entry sum |
| `posted_entries_cannot_be_updated_or_deleted` | the database refuses to mutate history |

## Stack

Java 21, Spring Boot 3 (Web, Data JPA / Hibernate), PostgreSQL, Flyway, Bean
Validation, springdoc OpenAPI, JUnit 5 and Testcontainers, Docker Compose, and a
GitHub Actions pipeline that runs the full suite on every push.
