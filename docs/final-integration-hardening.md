# Final Integration Hardening — P1 fixes before ChairX backend integration

**Branch:** `audit/final-integration-hardening` based on `fix/p17-complete-business-rbac`, which the owner reported passed `mvn clean verify` with 592 tests.

**Scope:** Two confirmed high-priority findings from the independent review. No new accounting engine, data reset, changes to legacy Flyway migrations, feature expansion or modification of inventory/sales algorithms.

## FI-01: Prevent expenses from altering closed financial periods

### Existing behavior and risk
`ExpenseService.create()` accepted arbitrary `expenseDate` while `CashFlowRepository` assigned journal entries to the *actual posting instant*. An expense inserted after a closing could change historical expense-date reports while its cash movement was correctly recognized in the present period.

### New rule
- If any `finance_daily_closings.business_date >= expenseDate`, the date falls within a sealed historical period and **a new expense posting is rejected** with existing `FinanceAccountOperationException` → HTTP **409 FINANCE_OPERATION_CONFLICT**.
- The finance account row is locked **before** checking the period, matching the lock used by daily closing. No closing race between validating `expenseDate` and posting.
- `ExpenseService` still inserts the expense and finance movement in the same transaction. On rejection both the document insert and any effects roll back. No cash/bank, financial journal or audit change.
- A retry with the *same* idempotency key and exact request content continues returning its existing committed expense without a second posting, including after closing. A changed payload on the same key remains a conflict.
- An open day remains writable. The current-day closing guard, balance checks, finance account initialization, currencies (KGS only), and other document flows remain unchanged.
- Legacy expenses / closed-day rows are **not mutated**. This is an application-level protection, not a database-level trigger, and direct SQL administrator writes remain outside this contract.

### Implementation
`FinancePostingService.postExpense` serializes with account locks, then calls `FinanceAccountRepository.rejectClosedDocumentDate`. `ExpenseService.create` uses the new method, without duplicating financial journal/business logic.

### Tests
`FinancialDocumentClosingTests` tests a legacy previous-day closing; attempts to create expenses dated that day and earlier; unchanged balance and journal/document counts on rejected calls; acceptance of an open-day expense; replay of a previously recorded expense after closing; and REST 409 / JSON contract with no side effects.

## FI-02: Configurable technical account name reserved in ADMIN bootstrap

### Existing behavior and risk
`CompositeUserDetailsService` always routes `CHAIRX_CATALOG_USERNAME` to the technical catalog principal, while `AdminBootstrapService` only prohibited the **literal** `catalog`. A changed technical username could shadow the first administrator's login.

### New rule
`AdminBootstrapService` now injects `@Value("${CHAIRX_CATALOG_USERNAME:catalog}")` matching `SecurityConfig` and `UserManagementService`. It rejects the configured technical username case-insensitively during bootstrap, before storing a user or role assignment. This does **not** change password hashes, HTTP Basic, bootstrap initialization, CSRF or the employee role model.

### Tests
`AdminBootstrapServiceIntegrationTest` configures `CHAIRX_CATALOG_USERNAME=service_catalog` and rejects both `service_catalog` and `SERVICE_CATALOG`, asserting no created users, assignments or completed bootstrap.

## Mandatory validation

From the repository root:

```bash
git fetch origin
git switch --track origin/audit/final-integration-hardening
cd backend
mvn clean verify
```

If the local branch already exists, use `git switch audit/final-integration-hardening && git pull --ff-only origin audit/final-integration-hardening` instead.

**Verification status:** tests were added and committed through the GitHub connector. The connector cannot run Java/Testcontainers; runtime results are **not yet confirmed**. Do not mark READY or merge until `mvn clean verify` succeeds and the integration diff is reviewed.

## Explicitly deferred P2 items

1. Define and implement an authorized reconciliation procedure for actual-vs-expected CASH/BANK discrepancies. **Never silently overwrite the balance or journal.**
2. Agree a controlled procedure for days missed at midnight in `Asia/Bishkek`; existing close method intentionally allows only the current Bishkek day.
3. Configure GitHub CI running Java 25, PostgreSQL 17 Testcontainers, Maven, and branch protection.
4. Deployment hardening: HTTPS, secrets, backup+restore test, role review and monitoring.
5. Frontend + CRM WhatsApp/Instagram, analytics/Excel are separate development stages.

A `BUILD SUCCESS` indicates tests pass, not automatically that these P2 policies are resolved or that the system is ready for production.
