# FLOW-31 — operational day and company financial close

## Current implementation (PR #82)

There are **two** independent confirmations, not three:

1. **Employee operational confirmation** — `GET/POST /api/finance/my-closings/{businessDate}`.
   The authenticated employee reads a company-wide operational view. It contains qualifying completed and fully paid sales, staff-created expenses, and a single expected/actual total. One authorized employee may confirm the day. The confirmation stores immutable sales/expense snapshots and an audit event; it does **not** modify finance account balances or ledger rows.
2. **Manager financial confirmation** — existing `POST /api/finance/closings/{businessDate}`.
   The manager must first have a company-wide operational confirmation for this date. The existing corporate closing reconciles CASH and BANK under the original account/date locks. An authorized system administrator retains the historical emergency path. Manager expenses are represented by normal `expenses`/finance postings, not by editing the employee snapshot.

## Business date and access

Business dates use `Asia/Bishkek`. Employees see operational reports for the last **30 calendar days** and have current/history (7/30) UI choices. Managers can consult past finance reports without the employee 30-day view limit.

Employees do not receive `FINANCE_READ` simply by having closing permissions. Their operational API does not expose company account balances. Expenses are created under the authenticated identity and remain subject to the standard expense RBAC. Manager finance writes require their persisted system role and the explicit finance/closing permissions.

## Database and historical records

Flyway V63 adds `company_wide` to `employee_daily_closings` and a partial unique index on `business_date` for company-wide records. Previous employee-personal rows are retained, distinct from a true company-wide confirmation; they must not falsely satisfy the manager's company-close prerequisite. Snapshots contain the original sale identifiers, quantities and expense authors. A second company-wide confirmation for the same date is rejected.

No new financial posting occurs when an operational report is confirmed. The manager's financial close continues to use the existing corporate ledger/snapshot code, permissions, corrections and audit policies.

## List and dashboard behavior

- Sales: operational view keeps today's relevant orders and older outstanding work; history remains separate.
- Deliveries: current view keeps old outstanding deliveries and hides old delivered/cancelled ones. History remains available.
- Purchases: default view is last seven days **plus older unfinished** `DRAFT`, `CONFIRMED` or `PARTIALLY_RECEIVED`, filtered in SQL before pagination. Manual date filters switch to exact historical selection.
- Dashboard: presents today's created orders, today's completed deliveries, outstanding deliveries and stock alerts from permission-scoped backend data.

## Verification and cautions

The PR #82 CI matrix includes full backend integration tests (PostgreSQL Testcontainers), frontend Vitest/TypeScript/ESLint/Vite build, Docker Compose smoke, F08 delivery PostgreSQL tests and F11 Chromium browser acceptance plus Flyway upgrade/restore. See the PR checks for the **current commit SHA**, not an older green revision.

The company operational **money** total is based on the day's posted SALE_PAYMENT ledger entries less staff expenses. The qualifying completed-and-paid **sales list** is calculated separately; these are different operational measures and need not have identical totals when deposits were taken or payments were received on another date. Do not relabel the expected money total as today's completed-sales revenue.

Before deploying on real business data, review edge cases of late payments/refunds, employee role changes after historical snapshots, and manager-entered expenses before/after final closing. Never replay closing POSTs automatically following an uncertain network response.
