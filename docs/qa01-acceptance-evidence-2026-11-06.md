# ChairX QA-01 — release acceptance evidence register

**Updated:** 2026-11-06. **Scope:** GitHub `main` after #243, commit `9088c7c4f978f6c07102b35813bbd43ae6461e10`.
**Tracking:** [QA-01 #96](https://github.com/Medetovichv/chairX/issues/96), [finance #128](https://github.com/Medetovichv/chairX/issues/128).
**Rules:** Successful CI proves only its executed assertions; it does not imply an unscripted user walkthrough or production signoff. Destructive/financial test scenarios run against disposable PostgreSQL / `chairx_test`, never a production database.

## Automated acceptance matrix

| Business area | Executable evidence (repository) | What has automated coverage | What remains unverified before QA-01 closure |
|---|---|---|---|
| Sales, pickup and payments | `backend/src/test/java/kg/chairx/sale/SaleTests.java`; `backend/src/test/java/kg/chairx/payment/PaymentTests.java`; `frontend/src/test/sales.test.tsx` | Draft/confirm/fulfill/cancel transitions, paid-order cancellation denial, stock reservation, payment/cancel concurrency | Staff-operated workflow on populated representative orders, device widths and multiple cashiers |
| Quick View / lost response | `SaleOperationReceiptApiTests.java`, `SaleOperationRaceHttpTests.java` under `backend/src/test/java/kg/chairx/sale/`; `frontend/src/shared/components/SaleQuickActions.test.tsx`; `frontend/src/test/sale-quickview-qa97.test.tsx` | Exact operation UUID receipt, replay isolation, actor-scoped response, concurrent operator race, fail-closed legacy journal | Manual real-browser network interruption during staff operation and subsequent support/reconciliation drill |
| Purchases and costing | `backend/src/test/java/kg/chairx/purchase/`; `backend/src/test/java/kg/chairx/inventory/cost/`; `frontend/src/test/purchases.test.tsx` | Purchase/receipt, inventory and FIFO integration suites | Manually inspect partially received multi-warehouse purchases and source-document traceability |
| Warehouses/transfers | `backend/src/test/java/kg/chairx/inventory/`; `frontend/src/test/inventory.test.tsx`; `frontend/src/test/studio-inventory-workspace.test.tsx` | Transfer, replay and stock state regressions | Populated warehouse list and zero-stock filters in all production-like viewport widths |
| Delivery workflows | `backend/src/test/java/kg/chairx/delivery/`; `frontend/src/test/deliveries.test.tsx`; `frontend/src/test/issue109-operational-delivery-status.test.ts` | City/region statuses, delivery boundaries, today's operational status filters | Cross-role manual handover, unpaid delivery and driver workflow in UI |
| Returns/defects/Exchange retirement | `backend/src/test/java/kg/chairx/returning/`; `backend/src/test/java/kg/chairx/refund/`; `frontend/src/test/returns.test.tsx`; `frontend/src/test/studio-defects-workspace.test.tsx` | Returned quantity, refund compensation, financial idempotency and supporting navigation | Manual partial-return and defective-item flow with populated sale/warehouse records; ensure no retired Exchange create UI |
| Finance and historical periods | `backend/src/test/java/kg/chairx/finance/`; `backend/src/test/java/kg/chairx/refund/RefundTests.java`; `backend/src/test/java/kg/chairx/refund/RefundApiTests.java` | Original journal preserved, refund posts on actual Bishkek date, current-day closure rejects partial posting, historical closed-day snapshot immutable, 403 RBAC/CSRF no BANK impact, idempotent refund | Authorized historical close followed by payment/refund via live HTTP, and cross-check actual reporting figures from representative records (#128) |
| Daily closing | `DailyClosingMultiDayAcceptanceTest.java` and `DailyClosingCorrectionIntegrationTest.java` under `backend/src/test/java/kg/chairx/finance/`; `frontend/src/test/daily-closing-maintenance.test.tsx` | Manager/admin scoped unlock, time limits, history/version, immutable original snapshots and dated adjustments | Actual employee-manager handoff and signoff with representative physical cash discrepancies |
| Roles, sensitive data | `backend/src/test/java/kg/chairx/security/`; `frontend/src/test/auth-ui.test.tsx`; full HTTP F11 suite | Unauthorized endpoints, role-filtered controls, anonymous/CSRF rejection and refund negative paths | Full custom-role configuration matrix (not only built-in roles), response-by-response raw JSON leakage audit |
| UI, migrations and recovery | `frontend/src/test/studio-*.test.tsx`; `.github/workflows/f11-release-gate.yml`, `backend-verify.yml`, `local-dev-smoke.yml` | Unit/component/route tests, Chromium cross-route responsiveness/keyboard coverage, migrated database and local stack smoke | Human visual review on populated data at 320/375/768 px, Back/Forward and interrupted operations, validated backup *restore* rehearsal against a representative snapshot |

## Verified merge gates

- **#97**: PRs #236–#240 merged, including real two-operator authenticated cancellation race and receipt isolation; Issue #97 closed.
- **#128**: #241 (HTTP refund replay after historical close), #242 (BANK refund after frozen historical closing), and #243 (refund RBAC/CSRF rejection) merged after Backend, Local smoke and complete F11 release gates. Financial *business logic* was not modified by #242/#243.
- **#96**: CI gates cover many automated paths. **Do not mark QA-01 complete** until remaining manual data-filled acceptance and explicit custom-role/security/restore drills are evidenced with dated screenshots, traces and exact run IDs.

## Repeatable release signoff procedure

1. On a clean disposable PostgreSQL clone, record `git rev-parse HEAD`, Flyway validation, migration/restore test logs and all required workflow run IDs.
2. Seed representative purchases, two warehouses, city/region deliveries, paid and unpaid pickup orders, partial returns and refunds. Use non-personal synthetic customer data.
3. Complete the remaining manual scenarios in the last column, noting environment, expected result, actual result, screenshot/browser trace, role, date and defect issue when a mismatch occurs.
4. Audit CASH/BANK totals, `finance_movements`, source documents and original closed snapshots before and after the scenarios; do not modify real shop books.
5. Close #128 only after its financially sensitive live HTTP and manual acceptance is supported; close #96 only after every matrix row has an explicit signoff. Never substitute 'CI is green' for this evidence.

This register is a reproducible inventory of **tested automation vs outstanding release acceptance**, not a claim that every operational scenario was personally executed.
