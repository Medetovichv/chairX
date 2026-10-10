# ChairX — backend development & release checklist

## Environment

- Java 25, Maven wrapper `backend/mvnw` (or installed Maven), Docker with PostgreSQL 17.
- Start local database: `docker compose up -d postgres`.
- Build/test (from `backend`): `./mvnw clean verify` or `mvn clean verify`.
- Tests start isolated PostgreSQL 17 Testcontainers database `chairx_test`. Test setup explicitly checks the database name. Never point integration tests to production data.
- Run API after setting environment variables (including `CHAIRX_CATALOG_PASSWORD`): `./mvnw spring-boot:run`. Spring Boot does not read `.env` automatically.

## Scope and sequencing

P20 is branched from verified P19, **not from P17**. Its new Flyway migrations are V37 `purchase_payments`, V38 `payments.payment_channel`, V39 read-only reconciliation view. Do not edit historical migrations V1–V36; compare P17 migration numbering before any eventual merge. P17 owns roles and permissions and must separately authorize new operational endpoints.

Keep the code a modular monolith; application services own transaction scope, repositories use Spring JDBC, and API controllers should only validate/request/dispatch. All financial mutations use FinancePostingService, account locks and immutable ledger entries. No direct SQL changing `finance_accounts.balance` outside controlled finance services.

## New P20 verification

Run `mvn clean verify`, inspect `backend/target/surefire-reports` on failure. Tests added:
- `PurchasePaymentTests`: partial/supplier/cargo payment caps, rollback on insufficient BANK balance, duplicate key and parallel retry, no inventory change, HTTP and purchase cancellation.
- `BackendMvpWorkflowIntegrationTests`: purchase 20 chairs, partial delivery to HOME/OFFICE, cargo/FIFO, supplier/cargo payments, multi-warehouse sale, bank settlement, account balances and CashFlow totals.
- `BackendReadApiIntegrationTests`: inventory balances/movement list without mutation, filtered lists for sale/delivery/return, defect open/wait/resolve, no duplicate write-off.
- Extended `PaymentTests`: M-Bank and Bank installment channels, compatibility, cancellation channel preservation.

Existing P18/P19 regression, FIFO, idempotency, refunds, exchange and closed-day tests remain required. Never skip or disable a failing test to claim completion. Test-specific imports, DB fixtures or assertions must be corrected with reasoned minimal patches.

## Manual smoke checklist (authenticated + CSRF)

1. Create supplier/product/variant; create purchase, confirm it, post partial SUPPLIER and CARGO payments, receive units into two active warehouses.
2. Ensure inventory balances and FIFO cost correct; no stock movement occurs on payment or GET.
3. Create two-location Sale, pay by MBANK, fulfill; verify BANK journal entry and ledger balance.
4. Fail a delivery: FAILED alone must not restock; explicit return-to-warehouse restores. Reject ordinary return until DELIVERED.
5. Create a delivered partial return and linked refund; test bounds. Exercise higher/lower/equal-value exchange with settlement.
6. Open defect, wait for parts, resolve or write off; ensure blocked/available and valued write-off correct.
7. Create expense, transfer CASH↔BANK, compare CashFlow `purchasePayments`/totalOut and daily closing for current Bishkek day.
8. Retry identical idempotency keys; confirm unchanged journal, FIFO and counters.

## Integration and release risks

- **P17**: verify security policies explicitly cover purchase payments, inventory listing, sale/delivery/returns lists, defect workflow. P20 does not modify `SecurityConfig` or roles.
- **Flyway**: resolve P17/P20 numbering collisions *before* applying to shared databases. Test upgrades from existing V36 schema, including legacy sales-payment rows with NULL channel.
- **Finance migration**: V37 alters CHECK constraints; ensure historical movement/source values stay allowed. V39 only extends diagnostics and must not backfill.
- **API compatibility**: `PaymentResponse` adds `channel`; CashFlow adds `purchasePayments`. Confirm frontend clients after integration.
- **Time**: posting-time CashFlow uses [from,to) Asia/Bishkek; managerial expenses still have their own expense date.
- **Production readiness**: separate deployment, secret-management, backup/restore, logging/monitoring, recovery, real DB migration and security acceptance tests still required. Maven green only validates application/test suite.

## Git delivery

Keep `feat/p20-backend-mvp` and its draft PR based on `fix/p19-finance-integrity`. Do not merge to P17 or main until security review and the full locally run verify result have been reviewed. Frontend is not part of P20.
