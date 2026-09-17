# Design

## Architecture

```
Client
  |
Spring Web MVC (controllers, Bean Validation, OpenAPI)
  |
Service layer (double-entry posting, locking, idempotency)
  |
Spring Data JPA / Hibernate  +  Flyway (versioned migrations)
  |
PostgreSQL (local: Docker Compose, tests: Testcontainers)
```

There is no message broker, no authentication and no cloud deployment: this is
the ledger core in isolation. The wider platform those concerns belong to is the
ABS system (see the README).

## Data model

Three tables. Every financial event produces balanced ledger entries; balances
are derived, never stored.

### accounts

| Column | Type | Notes |
|--------|------|-------|
| id | UUID | Primary key |
| name | varchar(255) | Unique, required |
| is_system | boolean | True for the External Clearing account |
| created_at | timestamptz | Server default |

### transactions

| Column | Type | Notes |
|--------|------|-------|
| id | UUID | Primary key |
| idempotency_key | varchar(255) | Unique, client-supplied |
| type | varchar | deposit, withdrawal, transfer (CHECK constraint) |
| amount | numeric(19,2) | BigDecimal, never float; CHECK (amount > 0) |
| request_hash | varchar(64) | SHA-256 of the canonical request |
| reference | varchar(255) | Optional |
| created_at | timestamptz | Server default |

### ledger_entries

| Column | Type | Notes |
|--------|------|-------|
| id | UUID | Primary key |
| transaction_id | UUID | FK to transactions, required |
| account_id | UUID | FK to accounts, required |
| amount | numeric(19,2) | Signed: positive credits, negative debits |
| created_at | timestamptz | Server default, used for statement ordering |

## Double-entry model

Every transaction produces exactly two entries that sum to zero.

**Deposit (50.00 into a customer):**
```
External Clearing   -50.00
Customer            +50.00
```

**Withdrawal (20.00 from a customer):**
```
Customer            -20.00
External Clearing   +20.00
```

**Transfer (50.00 from A to B):**
```
A   -50.00
B   +50.00
```

Global invariant: the sum of all entries across all accounts is always zero.
Per-transaction invariant: the entries of any one transaction sum to zero. This
is enforced in both places: the service asserts the pair sums to zero before
committing, and the database enforces it independently with a deferred constraint
trigger that rejects, at commit, any transaction whose entries do not sum to zero.
Foreign keys additionally refuse an entry that points at no transaction or
account. The trigger is deferred so both sides of the transaction are present when
the check runs.

## External Clearing

An internal system account is the counterparty for deposits and withdrawals, so
cash entering or leaving the system still balances double-entry. It is seeded by
the first migration with a fixed id, which also removes the cold-start race where
concurrent first transactions would each try to create it. It is a balancing
account, not a model of a production bank's chart of accounts.

## Transaction lifecycle

```
POST /transactions
  -> Bean Validation (amount > 0 and <= 2 dp, required string fields)
  -> normalise amount to 2 decimal places
  -> look up idempotency key
       key exists, same request hash  -> return original, HTTP 200
       key exists, different request   -> HTTP 409 Conflict
       key is new                      -> continue
  -> lock affected accounts (SELECT ... FOR UPDATE), in ascending id order
  -> balance check (withdrawal and transfer)
  -> build the transaction and its two entries; assert they sum to zero
  -> persist within one @Transactional unit of work (all or nothing)
  -> return the transaction, HTTP 201
```

## Idempotency

Each `POST /transactions` carries an `idempotency_key`. The service stores a
SHA-256 `request_hash` of the semantically relevant fields (type, amount,
account ids, reference).

- Same key, same hash: the original transaction is returned with **200** (safe
  retry, applied once).
- Same key, different hash: **409 Conflict** (a key reused with different
  parameters, which would otherwise silently return the wrong transaction).

The unique constraint on `idempotency_key` is the real guard. A concurrent
duplicate that loses the race surfaces as a `DataIntegrityViolationException`,
which is caught and turned into the same replay result, so two racing identical
requests still post exactly once.

## Concurrency

Before a balance check the affected accounts are locked with a pessimistic row
lock (`SELECT ... FOR UPDATE`) held to commit, which prevents the classic
double-spend:

```
Thread A reads balance 100
Thread B reads balance 100
Thread A withdraws 80 -> 20
Thread B withdraws 80 -> -60   (the bug the lock prevents)
```

With the lock, Thread B blocks until Thread A commits, re-reads 20, and correctly
refuses. Transfers lock both accounts in ascending id order, so two opposing
transfers between the same pair acquire the locks in the same order and cannot
deadlock.

## Money

Every monetary value is a `BigDecimal` mapped to `numeric(19,2)`, never a
floating-point `double`. Amounts are normalised to two decimal places on the way
in, so "10" and "10.00" are the same money and hash to the same idempotent
request.

## Append-only

Posted entries are immutable. There is no setter, no update or delete endpoint,
and a database trigger rejects any `UPDATE` or `DELETE` on `ledger_entries`, so
history cannot be rewritten even by a direct SQL statement. Corrections are made
by posting compensating entries, never by editing.

## Schema ownership

Flyway owns the schema. The single migration installs the tables, the CHECK and
UNIQUE and FOREIGN KEY constraints, the append-only trigger, and the seeded
clearing account. Hibernate runs in `validate` mode, so a drift between the JPA
mapping and the migrated schema fails fast at startup rather than silently.

## Configuration

The datasource URL, username and password come from environment variables
(`SPRING_DATASOURCE_*`), with local defaults for Docker Compose. No credentials
are committed to source control.

## CI

GitHub Actions runs `mvn verify` on Java 21 on every push, which compiles the
service and runs the full integration suite against a real PostgreSQL provided by
Testcontainers on the runner.
