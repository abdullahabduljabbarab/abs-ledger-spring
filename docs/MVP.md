# MVP Scope

## Objective

Re-implement the core of the ABS ledger on Java and Spring Boot, keeping the same
invariants, and enforce them both in the service and in the database.

## Must-have

- Create accounts
- Deposit, withdraw, transfer
- Immutable, balanced ledger entries (double-entry, External Clearing counterparty)
- Derived balances, computed from entries, never stored
- Idempotent writes with request-hash conflict detection (200 replay, 201 new)
- Atomic transactions (one database transaction)
- Concurrency protection (pessimistic row locking, deterministic order)
- Invariants enforced in PostgreSQL (CHECK, UNIQUE, FOREIGN KEY, append-only trigger)
- PostgreSQL for local development and tests (Docker Compose, Testcontainers)
- Versioned schema (Flyway)
- CI running the full test suite (GitHub Actions)
- OpenAPI documentation (`/swagger-ui.html`)

## Not in scope

- Authentication and authorization
- The tamper-evident hash chain, the transactional outbox and event publishing
  (these live in the full ABS ledger)
- Cloud deployment and infrastructure as code
- Frontend, Kubernetes, Kafka, Redis, microservices
- Real payment rails or real personal or financial data

## Definition of done

- `docker compose up --build` brings up PostgreSQL and the service.
- Swagger UI is reachable at `/swagger-ui.html`.
- All tests are green in CI against PostgreSQL on Java 21.
- README and the `docs/` set describe the invariants, the design and the V&V.
