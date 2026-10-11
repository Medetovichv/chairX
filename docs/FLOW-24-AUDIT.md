# FLOW-24 — UX/RBAC audit (working findings)

This is a **source-code and CI review, not a manual acceptance-test certificate**. Scope: frontend modules for sales, customers, deliveries, inventory, catalog, purchases, suppliers, returns, exchanges, defects, finance, daily closing and employee administration. API authorization remains backend-authoritative.

| Area | Review findings | Remaining validation |
|---|---|---|
| Sales | Sale detail uses scoped catalog/inventory/customer/delivery queries, related links and comments; no reported regression on latest passing CI | Browser/mobile E2E, all roles |
| Customers | Detail and sale navigation exist | Test duplicate contacts, responsive forms |
| Deliveries | Direct detail no longer depends on previous list navigation for sale number; no unauthorized sales query; tests included | Cross-role live browser walkthrough |
| Inventory | Warehouse names and filtered links present in inspected flows | End-to-end transfers and inventory permission variants |
| Catalog | Product/variant focused creation implemented in stacked flow | Real mobile ergonomics |
| Purchases and suppliers | Costful purchase history separated from receiving worklist; cost-free receipt-origin lookup added in FLOW-23 | Cross-role financial non-disclosure review |
| Returns | Detail displays human chair/warehouse labels and sale links gated by permissions | Manual state combinations |
| Exchanges | Fixed visible raw UUIDs in detail and gated related sale queries; **ExchangesPage still exposes UUID-only lookup and recovery identifiers** because no global search API currently exists. Must not remove recovery reference without safe reconciliation design. | Dedicated server-side human lookup and safe recovery UX |
| Defects | Variant/warehouse, receipt-origin selection and backend validation added and CI green in FLOW-23 | In-browser regression |
| Refunds | Replaced visible UUIDs and English backend terms in the form; sale detail is permission-scoped. Core GuardedPost/idempotency retained. | New CI and RBAC integration |
| Finance and closing | Previously passing F10/F11 checks cover targeted backend contracts | Full manual financial workflows |
| Employees and roles | Routes and controls checked for declared permissions; custom roles present | Exhaustive backend resource-level authorization matrix |

## Current code changes in PR #75
1. Delivery direct-link human sale number and scoped fetch; regressions.
2. Return action wording.
3. Exchange detail hides UUIDs in visible content, fetches readable sale numbers only with SALES_READ; regression.
4. Refund view uses nontechnical wording and permission-scoped human order number; financial test selectors updated.

## Blocking closure criteria
- All latest-head CI checks green (frontend, F08/F09/F10, F11 and Docker).
- Audit remaining UUID-only exchange lookup and no-idempotency recovery without jeopardizing operational safety.
- Run real browser tests at desktop and mobile widths with custom roles.
- Final rebase/integration of stacked PRs with main is FLOW-25, **not** FLOW-24. No merge authorization assumed.

## Nonnegotiables
No chair photos; no extra payment methods; no fake history; no disclosure of cost data outside authorized backend APIs; no automatic repeat of unconfirmed financial or stock mutations.
