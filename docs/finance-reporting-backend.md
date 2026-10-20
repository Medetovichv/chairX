# ChairX — Finance Reporting backend contract

All currency amounts are in KGS. Date ranges are **[from, to)** (start inclusive, end exclusive) using Asia/Bishkek calendar dates. API access is restricted by `FINANCE_READ` both in HTTP security matchers and method-level guards. Financial figures are assembled server-side from PostgreSQL; the frontend must **not derive profit from cash balances**.

## Read-only finance APIs

| Endpoint | Purpose | Limits |
|---|---|---|
| `GET /api/finance/ledger?from=YYYY-MM-DD&to=YYYY-MM-DD&page=0&size=50&account=CASH` | Financial posting journal; each posting has source type, source ID, account and signed amount | ≤366 days, 1–100 items/page |
| `GET /api/finance/profitability?from=...&to=...` | Recognized sales, returns, FIFO COGS, gross/operating profit, allocated accruals, other operating expense | ≤93 days |
| `GET /api/finance/model-margins?from=...&to=...` | Net variation-level sales, returns and original FIFO cost | ≤93 days |
| `GET /api/finance/analytics?from=2026-01-01&to=2027-01-01` | Monthly recognition and *separate* cash-flow series | 1–12 complete months |
| `GET /api/finance/inventory-valuation` | **Current** FIFO stock valuation by model, not historical balance | Current only |
| `GET /api/finance/purchase-obligations` | Supplier/cargo outstanding and prepayment balances | Current, confirmed/received purchases |

## Recognition and reconciliation principles

1. A self-pickup sale is recognized on `fulfilled_at`; city/region delivery is recognized on `delivered_at` after confirmed delivery. Pending or in-transit delivery is not recognized for P&L. Payments do **not** determine sales revenue.
2. The original COGS is based on `inventory_cost_allocations` joined to each sold item’s `SALE_OUT` movement. The sum of valued allocated quantities must equal the sold quantity and there must be one source movement. A missing/partial FIFO trail means **unverified profit, never zero-cost profit**.
3. A physical return decreases economic revenue at the original unit sale price and increases original FIFO cost recovery on the **return date**, even if the source sale occurred in a prior month. Restoration data comes from `inventory_cost_restorations`; partial restored quantities make profit unverified.
4. A refund associated via `return_id` settles the existing claim; it **must not** reduce recognized revenue again. Exchanges follow the same rule: the old return reverses original revenue/cost, while a new fulfilled exchange sale creates its own economic revenue/cost; the credit transfer is not another sale.
5. A refund without a physical return is treated as a price concession if the original sale was already recognized. If there was also a physical return for that sale but the refund is missing `return_id`, attribution is ambiguous; profit is suppressed until the legacy document is corrected.
6. F14 accrued expenses are recognized over their allocation period. Expenses linked to an accrual through `expense_accrual_payments` represent cash settlement and are **not charged again**. Unlinked ordinary expenses are recognized on `expense_date`.
7. Purchases are **inventory/obligations**, not immediate P&L COGS. Supplier and cargo overpayments are kept as separate advances rather than netting against a different obligation.
8. Transfers between CASH and BANK are journal movements, not external revenue or expense. Cash Flow uses actual posting timestamps and keeps cash movements distinct from P&L.
9. If `netProfitAvailable=false`, both `grossProfit` and `operatingProfit` should be treated as unavailable. Never render `null` as zero. Individual known components may remain visible for diagnostics.
10. The model-margin endpoint cannot unambiguously assign a standalone (no-return) rebate across multiple items. The affected model’s gross profit is `null`, not an invented proportional allocation.

## Regression coverage

- Fully unpaid, partly paid and paid fulfilled sales produce the **same** economic recognition.
- Original sale recognized in January, physical return and linked refund in February: February reverses original price and FIFO once.
- Partial returns restore only original FIFO cost for returned units, not the entire batch.
- A new sale linked to an exchange is recognized only when fulfilled; exchange credit is not a second sale.
- Standalone refunds are price concessions; legacy refunds that could duplicate a recorded return suppress profit.
- Accrual-linked expense settlements do not duplicate period expense.
- Stock valuation, supplier/cargo balances, model margin and full finance journal are protected by financial permissions.
- Backend and F10 PostgreSQL Testcontainers, F11 release gate and Docker smoke must pass on the final PR head.

## Frontend handoff

The backend-only PR deliberately **removes** an early, provisional frontend report page. Use the separate frontend UX specification for routing, dashboard cards, charts, table drilldowns and mobile layout. Do not infer missing financial values from money movements or substitute placeholders as numbers.
