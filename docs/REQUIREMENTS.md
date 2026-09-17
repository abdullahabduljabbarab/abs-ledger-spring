# Requirements

## Functional

| ID | Requirement |
|----|-------------|
| REQ-F-001 | The system shall allow creation of a uniquely named account, and reject a duplicate name with 409. |
| REQ-F-002 | The system shall record deposits as balanced ledger entries against the internal clearing account. |
| REQ-F-003 | The system shall record withdrawals as balanced ledger entries against the internal clearing account. |
| REQ-F-004 | The system shall execute transfers atomically, debiting one account and crediting another in a single database transaction. |
| REQ-F-005 | The system shall derive account balances from the sum of ledger entries, never from a stored balance field. |
| REQ-F-006 | The system shall return the original transaction, with HTTP 200, when a duplicate idempotency key is submitted with matching parameters. |
| REQ-F-007 | The system shall reject a duplicate idempotency key submitted with different parameters (409 Conflict). |
| REQ-F-008 | The system shall not expose any operation that modifies or deletes ledger entries; corrections use compensating entries. |
| REQ-F-009 | The system shall reject withdrawals and transfers where the source account has insufficient balance (422). |
| REQ-F-010 | The system shall reject self-transfers, where the source and destination are the same account (422). |
| REQ-F-011 | The system shall reject zero and negative transaction amounts (422). |
| REQ-F-012 | The system shall provide a per-account statement of entries in posting order, each with a running balance. |

## Non-functional

| ID | Requirement |
|----|-------------|
| REQ-NF-001 | All monetary values shall use `BigDecimal` and `numeric(19,2)`. No floating-point money. |
| REQ-NF-002 | Concurrent transactions against the same account shall not cause double-spending, via pessimistic row locking with deterministic lock ordering. |
| REQ-NF-003 | Database credentials shall come from environment variables, never from source control. |
| REQ-NF-004 | The CI pipeline shall compile the service and run the full automated test suite on every push. |
| REQ-NF-005 | After every committed transaction, the sum of all ledger entries across the system shall equal zero. |
| REQ-NF-006 | Every committed transaction shall have ledger entries whose sum equals zero. |
| REQ-NF-007 | Tests shall run against real PostgreSQL (Testcontainers); local development shall use PostgreSQL via Docker Compose. |
| REQ-NF-008 | Schema changes shall be managed through versioned Flyway migrations, with Hibernate validating the mapping against them. |
| REQ-NF-009 | Positive amounts, single-use idempotency keys and referential integrity shall be enforced by database constraints, not only by application code. |
| REQ-NF-010 | Posted ledger entries shall be immutable, enforced by a database trigger that rejects any UPDATE or DELETE. |
