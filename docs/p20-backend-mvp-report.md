# PACKAGE 20 — BACKEND MVP REPORT (Draft / awaiting verify)

1. **Base branch and commit:** `fix/p19-finance-integrity`, `1a39d4316d4540eabf6c57c37972c87a226409fb`.
2. **Working branch:** `feat/p20-backend-mvp`. See draft PR for current HEAD.
3. **Business features implemented in source:** P20-A partial supplier and cargo purchase payouts with idempotency, payment caps, independent receipt/cost state and finance posting; P20-B separate CASH/BANK_TRANSFER/MBANK/BANK_INSTALLMENT channel on sale payments with CASH/BANK account semantics.
4. **Added REST:** POST/GET `/api/purchases/{purchaseId}/payments`; GET `/api/inventory/balances`, GET `/api/inventory/balances/{warehouseId}/{variantId}`, GET `/api/inventory/movements`; GET `/api/sales`; GET `/api/deliveries`; GET `/api/returns`; POST/GET/list/wait/resolve/write-off `/api/defects`.
5. **Affected modules:** purchase, finance, payment, inventory, sale, delivery, returning, defect, common web error mapping, integration tests and docs. No P17 security package changes.
6. **Migrations:** V37 purchase payment table + finance movement/source CHECK extension, V38 optional legacy-compatible sales payment channel, V39 purchase payout addition to existing read-only reconciliation view. Check collision with still-separate P17 migrations **before merge**.
7. **Purchase payment verification in code:** one Purchase lock per attempted payment; supplier/cargo limits and already-paid cargo floor; one negative finance journal entry per successful payout; rollback on posting failure; unique idempotency key with normalized fingerprint; unchanged FIFO / warehouse on payout.
8. **Finance:** CashFlow `purchasePayments` category, included exactly once in `totalOut`; balance change remains via FinancePostingService, financial closing still checks business day using existing account locking.
9. **Inventory/FIFO:** Receipt cost recorded when physically received; payout does not allocate cargo or create receipt. Test covers HOME and OFFICE independent quantities and valued layers.
10. **Workflow tests:** New `PurchasePaymentTests`, `BackendMvpWorkflowIntegrationTests`, `BackendReadApiIntegrationTests` and extended `PaymentTests`. Existing P18/P19 Sale, Delivery, Return, Refund, Exchange, Defect, Finance and FIFO test suites remain part of full verification. P20.03–20.08 already have focused tests in existing suites; no duplicate second implementation of these workflows was introduced.
11. **API contracts:** DTOs, pagination, explicit available stock, compact list views, 400 query validation, 409 business conflicts, P20 payout idempotency; cashflow and payment responses get new documented fields.
12. **Documentation:** updated README, added `docs/architecture.md`, `docs/business-rules.md`, `docs/api.md`, `docs/development.md`.
13. **Build/test result:** **NOT EXECUTED through this GitHub connector**. No Maven/Java/Testcontainers runtime attached to this tool. Run `cd backend && mvn clean verify` on a machine with Docker and Java 25 before treating this as verified. Do not present it as BUILD SUCCESS.
14. **Remaining risks:** compilation/runtime test errors may still surface; explicit test of real PostgreSQL migration upgrade with existing data; business date at closing boundary; numerical cash report compatibility; recovery strategy for historical mismatches.
15. **P17 dependencies:** all added routes need a permission/authorization review; P17 branch and `SecurityConfig` intentionally unchanged; migration-version coordination is mandatory.
16. **Frontend readiness:** current APIs are intended to cover core MVP screens, **conditionally pending full integration tests and P17 security approval**. No frontend was created.
17. **Pull Request:** draft PR will be linked from GitHub, base `fix/p19-finance-integrity`, no merge.

This document is a *source-level implementation report*, not production sign-off. A green Maven build, a real Flyway upgrade rehearsal, deployment tests and security review are distinct gates.
