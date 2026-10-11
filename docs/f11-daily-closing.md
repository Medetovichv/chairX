# ChairX F11 — Daily Closing frontend, phase A

Base: main after F10 PR #30. Branch: feat/frontend-f11-daily-closing.
Scope: replace the DailyClosingPage placeholder and add a typed, isolated frontend API.
Do not change backend, Flyway, money ledger, financial balances or main.

## Backend contracts and rights

GET /api/finance/closings — last 100 report dates.
GET /api/finance/closings/{date}/access — reportExists, canEdit, accessStatus, canUnlock.
GET /api/finance/closings/{date}/preview — backend expected CASH/BANK for a Bishkek business date.
GET /api/finance/closings/{date} — signed versioned report: original/effective expectations, actuals, differences, notes, recalculated/requiresReview.
GET /api/finance/closings/{date}/sales — preliminary current-day data or immutable historical snapshot. Missing historical snapshot must never be fabricated by reading current live sales.
POST /api/finance/closings/{date} — actualCash, cashNote, actualBank, bankNote. No money postings.

Read needs DAILY_CLOSING_READ or FINANCE_READ. Close needs DAILY_CLOSING_WRITE or FINANCE_CLOSE, plus backend-verified date window. Access GET is advisory only: final date/permissions/concurrency validation is inside backend transaction.

Bishkek business date; future date blocked. Actuals are whole nonnegative som strings. If either actual is different from preview expectation, a note is required for that account (max 2000 chars). A concurrent payment between GET preview and closing may change expected: backend revalidates under database lock.

## Safety

Use existing F10 GuardedPost to confirm and persist an operation journal before the closing POST. No automatic retry on network errors, 409 or 5xx. Backend close does not accept a client idempotency key: no blind replay, no inference from a similar report. Lost-response cases must be reconciled manually before further action. A closed report never creates a second closing or modifies balances/journal. Exact amounts use string/BigInt precision checks.

## Phase B remaining

Versioned correction PUT with expectedVersion and reason; Manager/Admin unlock and admin-only revoke; read-only history with DAILY_CLOSING_AUDIT_READ; dated expense correction via DAILY_CLOSING_EXPENSE_CORRECT and stable idempotency key. UI must respect 13:00 Bishkek cutoff and server return values, without recreating P21 backend policy.

Phase A test matrix: readonly without FINANCE_READ; no unauthorized GET; no synthetic missing historical snapshots; notes for discrepancies; one guarded POST and backend confirmation; locked-day denial; requiresReview after recalculation. CI frontend lint, TypeScript, Vitest and Vite production build must be green before further work and PR acceptance. Browser+PostgreSQL real E2E is an outstanding release gate, not claimed.
