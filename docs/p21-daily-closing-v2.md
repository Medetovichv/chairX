# P21 — Daily Financial Closing v2: implementation notes

Status: **partially implemented, not runtime-verified — do not merge**. Source: `audit/final-integration-hardening`; feature: `feat/p21-daily-closing-v2`.

## Verified baseline (P21-A)

- `DailyClosingService.close` rejects every date other than today (Asia/Bishkek) and records `finance_accounts.balance` at call time. Both rules must change to support yesterday's closing.
- `FinanceAccountRepository.rejectClosedDocumentDate(date)` seals dates for which there is a closing on or after the document date. **Do not remove this protection globally.**
- `FinanceAccountRepository.changeBalance` forbids changing balances on a currently closed business day. A historical unlock must not bypass it.
- `FinancePostingService.postExpense` calls the document-date guard; ordinary `post` is shared by payment, refunds, transfers and purchasing. Restrict special historical corrections to the expense workflow, not shared `post`.
- `ExpenseService.create` already protects idempotency using a unique key and SHA-256 fingerprint and performs expense/journal write in one transaction; reuse it rather than duplicating expense creation.
- `finance_movements.created_at` currently records physical posting time without a distinct effective business date. These are separate facts.
- `finance_daily_closing_accounts` stores expected, actual and a generated difference, with a DB-level mandatory note whenever they differ. Never convert a difference to a fictional expense or silently update an account balance.
- `SecurityConfig` is deny-by-default and database RBAC is permission-based. Extend specific endpoints and seed the existing role/permission tables; do not relax the fallback matcher.

## Minimal-safe implementation design

1. Centralize clock-dependent decisions in a pure `DailyClosingAccessPolicy`, using an injected `Clock` and **Asia/Bishkek**. Normal editing includes report date and next day strictly before 13:00. Manager unlock eligibility ends at 00:00 on day six. Admin has no age cutoff. Grants expire after at most 60 minutes; manager grants are capped by day-six midnight.
2. Store unlock events in PostgreSQL and serialize changes to a date by locking its closing row. Concurrent unlock attempts cannot extend an active grant; revoked/expired grants remain in history. Enforce both original user permission and report-date grant on every write.
3. Separate report lifecycle (`CLOSED`) from editability (`LOCKED` / `TEMPORARILY_UNLOCKED`).
4. Before enabling historical calculations, add a stable effective `business_date` for journal entries while retaining immutable `created_at`. **Legacy rows must not be silently relabeled as historically accurate.** Reconcile account balances and per-document postings before trusting backwards replay. Discrepancies must fail clearly.
5. Expected closing balance for date D = reconciled current account balance minus journal amounts with `business_date > D`; under this algorithm, opening balances are represented by journal movements and missing postings are detected.
6. Historical expense correction creates an actual expense via existing `ExpenseService` plus exactly one posting, never an adjustment inferred from a discrepancy. An account can change only in the current open financial day.
7. **P21-I revision strategy:** preserve original daily-closing account snapshots and use a separate `finance_daily_closing_adjustments` table for updated expectations. A legitimate late expense atomically recalculates all later closed reports and audits before/after states. Newly introduced differences are exposed as `requiresReview`, not silently rewritten away.
8. Mutating reports requires row locking and an audit entry recording old and new values and reason; do not reopen or duplicate a `CLOSED` report. Authorization and the expiry must be rechecked under lock.
9. Add route-specific DB permissions for reading, writing, unlocking (manager/admin), expense correction and audit. Technical `catalog` has no financial permission; preserve Basic Auth and CSRF.
10. Introduce focused integration tests with fixed time and PostgreSQL 17 Testcontainers, then business E2E, concurrency, migrations upgrade and full `mvn clean verify`.

## Integration/acceptance gates

- Exact boundary: yesterday at 12:59:59 allowed; 13:00:00 denied.
- Manager grant issued on day five at 23:30 must expire by day-six midnight; admin grant has no such age cap.
- A difference requires a note; closing changes neither financial account balances nor journal.
- A late valid expense affects the dated expected balance, not physical posting timestamp. Idempotent retries do not double-debit.
- Closing yesterday after today's new payment must use yesterday's end-of-day balance, never today's.
- Current closed day remains financially sealed even if another report is temporarily unlocked.
- Closed later reports must retain original snapshots and receive audited, versioned updated expectations in the SAME transaction as the corrected expense; roll back all changes on any failure.
- REST negative tests must verify both HTTP errors and **zero persisted side effects**.
- Before merge: all existing and new tests, clean and migration-upgrade PostgreSQL, reconciled legacy data, production-ready diagnostics, and security review. Never claim `BUILD SUCCESS` without executing Maven.

## Work stages

P21-A baseline analysis (this file); P21-B deterministic policy; P21-C unlock persistence; P21-D historical audit/migrations; P21-E balance replay/reconciliation; P21-F late expenses; P21-G REST/RBAC; P21-H/I PostgreSQL integration, concurrency and end-to-end; P21-J final documentation and security audit.

**Note:** This design document is not evidence that any of the later stages have been completed.

## Implemented in the current feature branch (static implementation, not yet accepted)

- `DailyClosingAccessPolicy` and JUnit unit tests for Bishkek 13:00, five-day limits, sixth-day cutoff, and 60-minute expiry.
- V41 grants and per-date serialization rows, including grants issued before a late report exists; grant/revoke service stores history through `AuditService`.
- V42 role-specific scoped permissions; HTTP authorization remains deny-by-default and keeps Basic/CSRF.
- V43 separate financial movement `business_date` with legacy inferred provenance. `created_at` is unchanged.
- V44 optimistic report version. Existing closing response gains a compatible six-argument constructor.
- Dated balance reconstruction with journal/document reconciliation. Ambiguous legacy expenses cause explicit conflicts.
- Creating a yesterday report uses the reconstructed balance, while today still uses the live balance. No shortage/surplus auto posting.
- Report update is versioned, requires reason and scoped access, records before/after audit, and leaves account balances unchanged.
- Controlled correction reuses expense idempotency and journal posting; balances and report recomputation share the transaction.
- P21-I now recalculates later closed reports atomically using V45 while retaining the original snapshots and reporting both original and effective values. The current-day global money lock remains in force.
- REST endpoints: `GET /api/finance/closings/{date}/preview`, `/access`, `/history`, `PUT /api/finance/closings/{date}`, `POST /api/finance/closings/{date}/unlock`, `/lock`, `/expenses`.
- Existing `FINANCE_READ` and `FINANCE_CLOSE` paths retain backward-compatible authorization. Added negative PostgreSQL/HTTP-Basic integration checks for unlock, edit, revision conflict, revoke, and catalog denial.

### API expectations

`PUT /api/finance/closings/{date}` JSON fields:
`expectedVersion`, `actualCash`, `cashNote`, `actualBank`, `bankNote`, `reason`.
A zero or nonzero discrepancy is never a reason to change `finance_accounts.balance`.
`POST /api/finance/closings/{date}/unlock` takes only `{"reason":"..."}`; expiry cannot be client-selected.
`POST /api/finance/closings/{date}/expenses` uses the existing `CreateExpenseRequest` contract with an idempotency key, and `expenseDate` must equal URL date.

### P21-I historical-revision model (V45)

* `finance_daily_closing_accounts.expected_balance` and its stored generated `difference` remain the **original signed-at-closing snapshot**.
* `finance_daily_closing_adjustments.expected_balance` contains the **latest recalculated journal expectation**, keyed by report and account. It is not a second posting and does not update CASH/BANK balances.
* The `GET /api/finance/closings/{date}` response returns `cash.expected`/`bank.expected` as the effective recalculated amounts and `difference = actual - effectiveExpected`. It also includes `originalExpected`, `recalculated`, and `requiresReview` per account. UI should label the initial and recalculated expectations clearly.
* Original `actual` and `note` observations are never silently changed. If a historical posting introduces a mismatch in a later report, `requiresReview=true` signals that staff must reconcile it. The original note may need to be kept until the original snapshot remains valid.
* A correction for day D locks BANK and CASH, then date-specific rows in ascending date order, updates all closed reports with `businessDate >= D`, increments each changed report version, and records `REPORT_EXPENSE_CORRECTED` (D) or `REPORT_EXPECTED_RECALCULATED` (later reports). All occurs within the same transaction as expense + journal + live balance.
* A correction remains blocked if the *current physical posting day* is already financially closed; a historical report unlock never disables that global protection.
* The audit trail allows viewing before/after effective and original values and now records the originating `expenseId` for every affected report. Repeating an expense request with its original idempotency key cannot create another movement.
* V45 does **not** modify older Flyway scripts and the adjustment table is cascade-cleaned with its original report account, supporting test fixture teardown and preserving all old data on upgrade.

### Not yet validated / completion blockers

The user reported that an earlier version of P21 passed `mvn clean verify`; **the additional P21-H/P21-I commits in this branch have not been runtime-verified since that successful run**. Repeat the complete test suite under Java 25, Docker and PostgreSQL 17 Testcontainers.

Mandatory follow-up before acceptance:
1. Perform `cd backend && mvn clean verify` under Java 25 and Docker-enabled Testcontainers.
2. Exercise Flyway V41–V46 on a clean and existing database snapshot; check migrations and financial balances, especially previously backdated expenses.
3. Run and expand the new integration tests for late expense reconciliation (full/partial shortages), idempotency replay/conflicts, rollback on insufficient funds, and audited recalculation of later-closed days.
4. Run the new concurrent identical-expense and simultaneous-report-edit tests, and add coordinated late-expense-versus-closing and expiry-during-write transaction tests.
5. Run the full purchase/inventory/FIFO/sales/delivery/return/refund/closing end-to-end business scenario, with DB assertions.
6. Verify production permission matrix, upgrade strategy, CSRF, and REST error contracts.

Until those gates pass, this is **work in progress**, not Package 21 acceptance. No merge to `main` should take place.


### P21 regression fixes after 616-test run (2026-10-10)

The user ran `mvn clean verify`: **616 tests, 17 failures, no test execution errors**. Most new report-access HTTP tests returned 500 instead of 200/403. A likely root cause is binding Java `Instant` directly as a PostgreSQL JDBC parameter for TIMESTAMPTZ in `DailyClosingAccessService`. These parameter binds now use UTC `OffsetDateTime` for queries, grant insertion and revocation. **This diagnosis requires confirmation by rerunning tests and inspecting Surefire exception details if failures persist.**

Legacy test expectations were updated for the approved P21 contract: yesterday can be edited until 13:00 Asia/Bishkek, older days require an unlock, and `ProductPersistenceTests` expects V41/V45 tables. `BusinessRbacMigrationIntegrationTests` now checks V39-to-current migrations rather than assuming only V40. V46 ensures `ADMIN` receives the full permission set, including the managerial unlock permission, while business decisions still prefer `DAILY_CLOSING_UNLOCK_ADMIN` without a five-day age cutoff. Preexisting Flyway scripts V41-V45 remain untouched (checksum-safe).

Mandatory retest: run targeted `DailyClosingBoundaryIntegrationTest,DailyClosingCorrectionIntegrationTest,DailyClosingUnlockIntegrationTest,DailyClosingIntegrationTest,ProductPersistenceTests,BusinessRbacMigrationIntegrationTests,SecurityRoleRepositoryIntegrationTest` under Java 25 + PostgreSQL 17, then `mvn clean verify`. Do not report the suite as passing until actually executed.

### P21-H/P21-I new test classes

- `DailyClosingCorrectionIntegrationTest`: PostgreSQL business-date transfer replay, full and partial shortage correction, exactly-once idempotency (including concurrent requests), rollback on insufficient money, rollback on negative historical balances, preservation of original snapshots, audited recalculation of later closed reports, concurrent versioned edits, and race between next-day close and backdated posting.
- `DailyClosingBoundaryIntegrationTest`: fixed Clock with real HTTP Basic employees, 12:59:59 vs 13:00:00 boundary, 59:59 vs 60:00 expiry, manager day-five cutoff and day-six administrator grant.
- `DailyClosingMigrationUpgradeTest`: isolated PostgreSQL schema migration from Flyway V40 to V45, preservation of existing movements, dates, cash and closing snapshots, successful creation of a separate recalculated expected amount.

These tests have been **committed, not reported as passing**. No merge before an actual `mvn clean verify` after P21-I.
