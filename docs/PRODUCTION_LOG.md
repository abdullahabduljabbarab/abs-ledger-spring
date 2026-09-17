# Production Log

A running record of what was built and verified, in what order. Newest last.

## Milestone 1: The ledger core

**Goal:** a double-entry ledger on Spring Boot with the ABS invariants enforced in
both the service and the database.

**Built:**
- Domain model (`Account`, `Transaction`, `LedgerEntry`, `TransactionType`) with
  `BigDecimal` money mapped to `numeric(19,2)`.
- `LedgerService`: deposit, withdrawal and transfer as balanced entry pairs
  against a seeded External Clearing account; balances derived from entry sums;
  idempotency by key plus request hash; pessimistic account locking in ascending
  id order for the balance-checked paths.
- REST surface: `POST /accounts`, `POST /transactions`,
  `GET /accounts/{id}/balance`, `GET /accounts/{id}/entries`, with a single
  structured error handler. Replay returns 200, a new posting 201.
- Flyway migration installing the schema, the `CHECK (amount > 0)`, the unique
  idempotency key, the foreign keys, an append-only trigger on `ledger_entries`,
  and the seeded clearing account. Hibernate runs in `validate` mode against it.
- OpenAPI via springdoc at `/swagger-ui.html`.

**Verified:**
- Integration tests against a real PostgreSQL (Testcontainers): deposit,
  withdrawal, overdraft refusal, transfer, statement with running balance,
  idempotent replay (200) and key-reuse conflict (409), duplicate account name,
  self-transfer and transfer overdraft refusals, unknown-account 404, and
  validation of non-positive amounts.
- The concurrency and invariant checks: 100 parallel transfers at one account
  pair leaving the books balanced and the global entry sum at zero, the
  per-transaction zero-sum invariant, and the database rejecting any UPDATE or
  DELETE of a posted entry.

**Packaging:** multi-stage Dockerfile (build on a JDK image, run on a JRE),
Docker Compose for the app plus PostgreSQL, and a GitHub Actions workflow running
`mvn verify` on Java 21 on every push.

**State:** complete. The ledger core stands on its own; authentication, the hash
chain, the outbox and cloud deployment remain out of scope by design and live in
the full ABS ledger and platform.
