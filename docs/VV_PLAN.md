# Verification and Validation Plan

## Approach

Every requirement is verified either by an automated test that runs against a
real PostgreSQL (Testcontainers), or by a design decision recorded here. CI
evidence is the GitHub Actions workflow, which runs `mvn verify` on Java 21 on
every push. Test names below are the JUnit methods in
`src/test/java/com/abs/ledger`.

## Requirement-to-test mapping

| Requirement | Verification | Test / evidence |
|-------------|--------------|-----------------|
| REQ-F-001 | Automated | `duplicate_account_name_is_conflict`; account creation exercised across the suite |
| REQ-F-002 | Automated | `deposit_credits_the_account` |
| REQ-F-003 | Automated | `withdrawal_debits_and_cannot_overspend` |
| REQ-F-004 | Automated | `transfer_moves_money_and_conserves_the_pair` |
| REQ-F-005 | Automated | `deposit_credits_the_account`, `withdrawal_debits_and_cannot_overspend` (balance read back) |
| REQ-F-006 | Automated | `duplicate_idempotency_key_replays_with_200_and_applies_once` |
| REQ-F-007 | Automated | `same_key_different_parameters_is_a_conflict` |
| REQ-F-008 | Automated + Inspection | `posted_entries_cannot_be_updated_or_deleted`; no mutating endpoint exists |
| REQ-F-009 | Automated | `withdrawal_debits_and_cannot_overspend`, `transfer_with_insufficient_funds_is_rejected` |
| REQ-F-010 | Automated | `self_transfer_is_rejected` |
| REQ-F-011 | Automated | `negative_amount_is_rejected_by_validation` |
| REQ-F-012 | Automated | `statement_lists_entries_with_running_balance` |
| REQ-NF-001 | Automated + Inspection | balance assertions at 2dp; `numeric(19,2)` columns, `BigDecimal` throughout |
| REQ-NF-002 | Automated | `concurrent_transfers_keep_the_books_balanced` |
| REQ-NF-003 | By design | datasource from `SPRING_DATASOURCE_*`; `.gitignore` excludes local artifacts |
| REQ-NF-004 | CI evidence | GitHub Actions `mvn verify` on push |
| REQ-NF-005 | Automated | `concurrent_transfers_keep_the_books_balanced` asserts the global sum is zero |
| REQ-NF-006 | Automated | `every_transaction_balances_to_zero`, and `an_unbalanced_transaction_cannot_be_posted_by_direct_sql` (database-enforced) |
| REQ-NF-007 | By design + Automated | `AbstractIntegrationTest` runs a real PostgreSQL container; `docker-compose.yml` for local |
| REQ-NF-008 | CI evidence | Flyway migration applied to a clean database before every test run |
| REQ-NF-009 | Automated + Inspection | `balance_of_unknown_account_is_404`; CHECK / UNIQUE / FK declared in `V1__init.sql` |
| REQ-NF-010 | Automated | `posted_entries_cannot_be_updated_or_deleted` (the database trigger rejects the writes) |

## Acceptance criteria

The project passes V&V when:

- Every requirement has an automated test or a recorded by-design justification.
- CI is green against PostgreSQL on Java 21.
- `docker compose up --build` brings the service up and Swagger UI is reachable
  at `/swagger-ui.html`.
