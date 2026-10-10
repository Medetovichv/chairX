# ChairX P22 — Operational Workflows & Reporting Contract

Status: single draft PR #15, branch `feat/p22-operational-workflows`.
Target: `main`. Do not merge pending required CI and business review.

## Scope and implementation map

- P22-A: optional customer/recipient names. Customer DTO, service/domain,
  persistence and V47. At least one contact is required; nullable PostgreSQL
  constraints, no deleting/modifying historical customers.
- P22-B: optional planned delivery date and rescheduling, separate planned
  date filtering and audit. Delivery DTO/domain, service, repository, controller,
  permission gate and V48. Existing constructors/route semantics preserved.
- P22-C: DRAFT sale order workflow and idempotency. Separate
  `CreateDraftSaleRequest`, `UpdateDraftSaleRequest`, status, service/repository
  endpoints, new comment field, V49. Current POST /api/sales is unchanged.
- P22-D: aggregated sale list, secure paginated client order history,
  aggregate delivery list, inventory overview (all/individual warehouses with
  per-warehouse UUIDs), purchasing overview (goods/cargo/receipts).
- P22-E: read-only completed sales and persistent snapshots, V50. Separate
  finance_daily_closing_sales_snapshots marker and line details. Added to
  DailyClosingService in the same transaction after original CASH/BANK
  account snapshot, without changing finance amounts or status transitions.
- P22-F: RBAC on explicit routes, integration tests for nullable names,
  planned dates, drafts, immutable daily sales and delivery boundaries.
  Full Maven build and GitHub Actions must be checked before marking ready.

## Domain and timeline

| Event | SaleStatus | DeliveryStatus | Stock | Day completion |
|---|---|---|---|---|
| Draft created/edited | DRAFT | none | no reservation | no |
| Draft confirmed | CONFIRMED | none | reserve items once | no |
| Pickup fulfilled | FULFILLED | none | SALE_OUT | YES at sale.fulfilledAt |
| Delivery dispatched | FULFILLED | IN_TRANSIT | SALE_OUT | no |
| Delivery accepted | FULFILLED | DELIVERED | no new stock movement | YES at delivery.deliveredAt |
| Finance daily close | no status mutation | no status mutation | no inventory mutation | saves immutable snapshot |

No new purchase transit status and no supplier sidebar logic. Currency is
Kyrgyz som; business midnight uses Asia/Bishkek.

## Security

All new GETs have explicit read permissions. Draft creation: SALES_CREATE,
draft editing/confirm: SALES_UPDATE, cancel: SALES_CANCEL. Planned-date
mutation: DELIVERIES_MANAGE; sales history requires both SALES_READ and
CUSTOMERS_READ. Completed sales endpoint accepts DAILY_CLOSING_READ or
FINANCE_READ; this does not issue FINANCE_READ to employees. Keep HTTP Basic,
CSRF protection and `anyRequest().denyAll()`.

## Concurrency and persistence

- Draft `create` has unique idempotency_key plus fingerprint. Existing key
  + different payload gives conflict. Confirmation locks the sale row,
  validates references, reserves sorted inventory items and flips status
  in a single database transaction. Double confirm never reserves twice.
- Closing snapshot and physical completion obtain the same PostgreSQL
  transaction advisory lock **before** their operational locks.
  The lock is released on commit/rollback, avoiding untracked in-progress
  events. The lock does not change P21 BANK→CASH internal order.
- Snapshot rows contain historical name/phone/products/amount/payment status,
  instead of deriving a finished report from subsequent mutable sales data.
  Read returns original frozen rows once closed.
- Existing historical P21 closings have no P22 metadata marker. `closed=true`
  plus `snapshotAvailable=false` and NULL totals explicitly means
  unavailable historical snapshot. No retroactive data backfill.
- Late-completed sales counted separately from immutable snapshot, with no
  financial postings/expenses fabricated.

## DB upgrades

- `V47__p22_nullable_names.sql`
- `V48__p22_planned_delivery_date.sql`
- `V49__p22_draft_sales.sql`
- `V50__p22_daily_closing_sales_snapshot.sql`

Original V1–V46 migrations remain unmodified. Review migration upgrades
against an existing database copy before production deployment.

## API examples

See `docs/api.md`, section "P22 — Operational lists and daily sales", for
all routes, query parameters, JSON requests/responses and permissions.

## Verification and remaining release checks

- Use Java 25, PostgreSQL 17 Testcontainers; in `backend`: `mvn clean verify`.
- Confirm both GitHub Actions workflows are GREEN for the **latest** commit,
  not a prior one. Previous green runs do not validate later changes.
- Verify concurrent delivery completion/closing and draft confirmation
  using PostgreSQL integration tests. Perform migration-upgrade verification
  from a prior P21 schema and client/frontend smoke tests before merge.
- Review security 401/403, CSRF, null handling, query limits and immutable
  snapshots. Do not merge automatically.
