# P21 — Daily Financial Closing v2: implementation notes

Status: **in progress — do not merge**. Source: `audit/final-integration-hardening`; feature: `feat/p21-daily-closing-v2`.

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
7. **Safe MVP rule for later closed reports:** if there is any already-closed business date after the corrected date, reject the historical posting with a specific conflict rather than silently rewriting later immutable snapshots. A later revisioned-corrections process can relax this only after implementing transactional revisions and audit.
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
- Closed later reports cannot be made inconsistent. Reject if safe automatic correction is not yet available.
- REST negative tests must verify both HTTP errors and **zero persisted side effects**.
- Before merge: all existing and new tests, clean and migration-upgrade PostgreSQL, reconciled legacy data, production-ready diagnostics, and security review. Never claim `BUILD SUCCESS` without executing Maven.

## Work stages

P21-A baseline analysis (this file); P21-B deterministic policy; P21-C unlock persistence; P21-D historical audit/migrations; P21-E balance replay/reconciliation; P21-F late expenses; P21-G REST/RBAC; P21-H/I PostgreSQL integration, concurrency and end-to-end; P21-J final documentation and security audit.

**Note:** This design document is not evidence that any of the later stages have been completed.
