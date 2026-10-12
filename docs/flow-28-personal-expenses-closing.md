# FLOW-28 — Personal expense attribution and manager reconciliation

## Business rule

Expenses already contain immutable `created_by` (`createdBy` in responses) and `expense_date`. No new responsible-person field or editable ownership is introduced.

* Personal view: **all completed company sales** for selected Bishkek day are displayed for transparency. The personal collection calculation still uses only sale payments posted by the authenticated username (`finance_movements.created_by`) on that business date. A sale created by another employee is not counted as this user's cash.
* Personal expenses: the API **only selects expenses where `created_by = authenticated username` and `expense_date = requested date`**. A client-provided employee ID or username is never accepted. The UI has only own expenses.
* Personal amount to reconcile for each account: personal collected amount minus creator-attributed expenses for that account; the server re-computes it before accepting close. Closing writes **no financial ledger entries**.
* When an employee closes a day, the expense rows are snapshotted to `employee_daily_closing_expenses`; subsequent expenses do not rewrite historical personal expense rows. The `collectionsChangedAfterClose` flag indicates that later activity changed the live calculation.
* Manager view: separately secured `GET /api/finance/employee-closings/{date}`, available only to an active real system `MANAGER` or `ADMIN` with `FINANCE_READ` and `DAILY_CLOSING_READ`. Provides closing state and expected/actual reconciliation across employee reports and **all** expenses with their original `createdBy`.
* Managers/admins can switch between their own personal closing and company closing; employee UI never requests the manager API or global balances.
* Expenses recorded by one person on another's behalf are attributable to the **creator**. No owner reassignment UI.

## Boundaries and known limitations

This model is an accountability report, not an alternative company ledger. Company balances, refunds, cancellations, purchase payments, and any cash-flow not assigned to an employee remain in existing corporate finance. Personal and corporate closing totals must not simply be added as if they were independent financial transactions.

Personal close currently requires nonnegative reconciled CASH and BANK values. When creator-owned expenses exceed personally received funds for an account, personal closing is blocked for manager review rather than storing an invalid negative closing balance. The manager still sees all the expense records.

The manager endpoint lists currently active users, including those without closings; expense reports contain the original usernames. Reports use Asia/Bishkek business date.
