# Threat Model (STRIDE)

## Scope

This model covers `abs-ledger-spring`: the ledger core in isolation, run locally
or in CI, with no authentication layer and no cloud deployment of its own. It is
designed to sit behind the ABS platform's boundary, which owns authentication,
network exposure and rate limiting. Out of scope here: authentication and
authorization, network-level DDoS, TLS termination, and cloud infrastructure.

## Assets

| Asset | Sensitivity | Location |
|-------|-------------|----------|
| Ledger entries | High (financial record) | PostgreSQL |
| Account balances | High (derived from entries) | Computed at query time |
| Database credentials | Critical | Environment variables |

## Threat analysis

### S: Spoofing

| Threat | Mitigation | Status |
|--------|------------|--------|
| A caller acts as another party | No in-service authentication by design; the service is meant to run behind the platform boundary that authenticates callers. Standalone it is not internet-exposed. | Accepted for this scope (see gaps) |

### T: Tampering

| Threat | Mitigation | Test |
|--------|------------|------|
| Direct modification or deletion of a posted entry | Append-only trigger raises on any UPDATE or DELETE of `ledger_entries`; no setter and no mutating endpoint exist | `posted_entries_cannot_be_updated_or_deleted` |
| An entry pointing at no real transaction or account | FOREIGN KEY constraints on `ledger_entries` | enforced by schema |
| A non-positive or unbalanced posting | `CHECK (amount > 0)`; a deferred constraint trigger rejects a transaction whose entries do not sum to zero at commit, independently of the service | `every_transaction_balances_to_zero`, `an_unbalanced_transaction_cannot_be_posted_by_direct_sql`, `negative_amount_is_rejected_by_validation` |
| SQL injection through request fields | Spring Data JPA parameterised queries; inputs bound as typed `UUID` and `BigDecimal`, so injection strings fail to parse before reaching SQL | type-safe binding |

### R: Repudiation

| Threat | Mitigation | Test |
|--------|------------|------|
| A caller denies making a request | Each transaction carries a caller-supplied `idempotency_key` and a `request_hash` of the canonical request, plus a `created_at` timestamp | `duplicate_idempotency_key_replays_with_200_and_applies_once` |

### I: Information disclosure

| Threat | Mitigation | Status |
|--------|------------|--------|
| Stack traces or internals in responses | Errors are mapped to a structured `{ "detail": ... }` body by a single handler; no stack traces are returned | `ApiExceptionHandler` |
| Credentials in source control | Datasource credentials come from `SPRING_DATASOURCE_*` environment variables; none are committed | by design |
| Enumeration via sequential ids | Accounts and transactions use UUIDs, not sequential integers | by design |

### D: Denial of service

| Threat | Mitigation | Status |
|--------|------------|--------|
| Malicious or malformed payloads | Bean Validation rejects bad types, missing fields and non-positive amounts before the service runs | `negative_amount_is_rejected_by_validation` |
| Unbounded statement reads | The statement endpoint returns all of an account's entries and is not yet paginated | Gap (see below) |

### E: Elevation of privilege

Not applicable in this scope: the service has no roles or privileged operations.
Authorization is a platform-boundary concern.

## Gaps not closed in this scope

| Gap | Risk | Note |
|-----|------|------|
| No authentication or authorization | High if exposed directly | Intended to run behind the ABS platform boundary; would be added before standalone exposure |
| No rate limiting | Medium | Would be added at the gateway |
| Statement endpoint not paginated | Low | Fine for the demo's data sizes; would add cursor pagination for production |
