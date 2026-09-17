# Security

## What this project is

A portfolio demonstration of the ledger core on the Spring stack. It is not a
production banking system and handles no real personal or financial data.

## Boundaries

**Secrets.** The datasource URL, username and password come from environment
variables (`SPRING_DATASOURCE_*`), with local-only defaults in
`docker-compose.yml`. No credentials are committed to source control.

**Input validation.** Requests are validated with Bean Validation before the
service runs: types, required fields, positive amounts and at most two decimal
places. Invalid requests are rejected with a structured error.

**SQL injection.** All access is through Spring Data JPA with parameterised
queries. Request identifiers are bound as typed `UUID` and amounts as
`BigDecimal`, so injection strings fail to parse before any SQL runs.

**Identifiers.** Accounts and transactions use UUIDs; no sequential integer ids
are exposed.

**Error responses.** A single exception handler returns structured
`{ "detail": ... }` bodies. No stack traces or internal state are leaked.

**Immutable history.** Posted entries cannot be changed or removed; a database
trigger enforces this independently of the application.

## Authentication

None, by deliberate scope. This service is the ledger core and is meant to run
behind the ABS platform's boundary, which owns authentication and authorization.
Adding token auth here would be straightforward but is not the point this repo
makes.

## Known limitations

- No authentication or authorization (scope decision above).
- No rate limiting.
- The per-account statement endpoint is not paginated.
- TLS is expected to be terminated by a fronting proxy in any real deployment,
  not by the application.
