# Service Level Objectives

## Targets

These are the objectives the ledger core is designed to meet. A read is a single
indexed query; a posting is one short transaction with a row lock and two inserts.

| Metric | Target | Rationale |
|--------|--------|-----------|
| Availability | 99.5% non-5xx under load | A ledger must stay correct and available under concurrent use |
| Read p95 | < 200ms | Balance and statement reads touch only this database |
| Posting p95 | < 500ms | A posting is one local transaction, no cross-service calls |
| Error rate (5xx) | < 1% | Expected 4xx (422 insufficient funds, 409 conflict) are not failures |
| Correctness | 100% | Zero invariant violations under concurrent load |

## How this repo verifies them

This repository is the ledger core, run locally and in CI, not a deployed
service, so it does not publish measured latency numbers of its own. What it
verifies instead is **correctness under concurrency**, which is the objective that
actually matters for a ledger:

- `concurrent_transfers_keep_the_books_balanced` fires 100 parallel transfers at
  one account pair and asserts the books still balance and the global sum is zero.
- `every_transaction_balances_to_zero` and the append-only trigger test confirm
  the per-transaction and immutability invariants hold.

The measured latency and throughput story for a deployed instance of this design
lives in the original ABS ledger, which was load-tested live on Cloud Run; see
its `docs/SLO.md`:

- https://github.com/abdullahabduljabbarab/ledger-api

## Measurement approach for a deployment

If deployed, the objectives above would be measured the same way the ABS ledger's
were: a Locust run against the live service, reporting availability, the latency
percentiles, and the audit result after the run, with the raw numbers recorded
here rather than asserted.
