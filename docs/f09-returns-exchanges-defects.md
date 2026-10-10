# F09 — Returns, Exchanges & Defect Management

## Goal, boundaries, architecture
Implementation of real physical returns, exchanges and manufacturing/warehouse defects for ChairX office chairs. React 19/TypeScript/TanStack Query, with shared AppRouter, PageHeader, PageTabs, AuthProvider, ApiClient, inventory, catalog, sales and existing mobile-friendly controls. Source of truth is Spring backend + PostgreSQL.

Scope: `frontend/src/features/returns/` only, existing navigation/router integration, tests and docs, isolated CI. **No backend/Flyway changes, no new permissions, no stock balance mutation in browser. No F10 client payment, refund or exchange settlement posting.**

Routes: `/returns`, `/returns/new`, `/returns/:returnId`, `/returns/exchanges`, `/returns/exchanges/:exchangeId`, `/returns/defects`, `/returns/defects/:defectId`. Static paths precede dynamic ones. Existing `/exchanges` and `/defects` legacy redirects remain. Existing Sidebar menu grouping is unchanged. PageTabs uses current effective read/create permissions; custom roles work.

## Returns: real API, quantities, eligibility
`GET /api/returns?saleId=<UUID>&page=<n>&size=20` has `items,page,size,total`. A summary only contains `id,saleId,warehouseId,createdAt,reason,createdBy`. There is **no Return status**, global name search, sale number filter or client cancellation action. No fake status, F09 list client search, or replacement original sale.

`GET /api/returns/{id}` gives `id,saleId,warehouseId,items:[{id,saleItemId,quantity,condition}],reason,comment,createdBy,createdAt`. A `RETURNS_READ` operator can view permitted fields even without SALES_READ; the original sale is loaded only when SALES_READ is granted.

`POST /api/returns` with RETURNS_CREATE:
```json
{"saleId":"<sale UUID>","warehouseId":"<active warehouse UUID>","idempotencyKey":"<UUID>",
 "items":[{"saleItemId":"<SaleItemResponse.id>","quantity":1,"condition":"SELLABLE"}],
 "reason":"Кресло возвращено клиентом","comment":null}
```
**Use `saleItemId`, NOT variant UUID.** Each return has >=1 unique saleItemId and positive integer quantity, required reason 1..500 characters, optional comment <=1000.

For user-facing eligible quantities, load SaleResponse under SALES_READ, then ALL pages of `GET /api/returns?saleId=<UUID>` under RETURNS_READ and full return detail for each summary. Guard inconsistent pages/count and an excessive history. Available = original sale item quantity minus all previous returns for that `saleItemId`; never advertise an unproven remaining quantity. The backend holds the Sale row lock and verifies maximum again, including concurrent returns.

Customer return is permitted by backend only after actual fulfillment: SELF_PICKUP => SaleStatus FULFILLED; CITY_DELIVERY or REGION_DELIVERY => Sale FULFILLED AND **DeliveryStatus DELIVERED**. The frontend uses a real server `SaleSummary.deliveryStatus` obtained via `GET /api/sales?number=<saleNumber>` and exact sale UUID match, not inferred merely from Sale FULFILLED. READY, IN_TRANSIT and FAILED never produce a customer Return. FAILED physical goods use F08 `POST /api/deliveries/{id}/return-to-warehouse` and no second `POST /api/returns`.

Conditions are only SELLABLE (returns physically into available stock) and BLOCKED (RETURN_IN **and** blocked count); backend performs inventory movement and cost layer update. Never automatically call Defect API for BLOCKED goods: that would double-block the same units. Backend does not expose a direct ReturnItem→Defect linkage, so it is documented but not invented.

## Return idempotency and uncertainty
Each initial physical operation gets one `idempotencyKey`. It is persisted **before POST** in tab-scoped `sessionStorage`, with only user scope, operation UUID and sale UUID; no customer names, phone, reason, comments, passwords, or financial secrets in storage. During a network/5xx/409 uncertainty, the original payload/key remain in component memory for optional exact replay; no new key or different request may be sent. After reload the safe operation marker survives but the sensitive free text payload is intentionally not restored; operation stays locked until manual reconciliation. A new tab or clearing storage loses the local marker; do not retry before verifying backend. No fictitious idempotency lookup endpoint was added. Definitive 4xx may clear the marker.

## Exchanges: no fake global collection
Backend supports ONLY:
`POST /api/exchanges` (EXCHANGES_CREATE),
`GET /api/exchanges/{id}` (EXCHANGES_READ),
`GET /api/exchanges/{id}/settlements` (EXCHANGES_READ).
**`GET /api/exchanges` does not exist.**
`/returns/exchanges` is an actual create/open-by-known-ID screen, **not** a pretend all-exchanges list. It accepts a physical Return UUID, fulfillment type SELF_PICKUP/CITY_DELIVERY/REGION_DELIVERY and >=1 real selected variant/warehouse, quantity and whole-som sale price. Selection reuses F03 catalog and F04 inventory through the existing F07 `SaleItemsEditor`; no duplicate product browser or independent new sale POST.

Creation `POST /api/exchanges`:
```json
{"idempotencyKey":"<one UUID>","returnId":"<physical return UUID>",
 "fulfillmentType":"SELF_PICKUP",
 "items":[{"productVariantId":"<UUID>","warehouseId":"<UUID>","quantity":1,"unitSalePrice":"9500"}]}
```
Backend ExchangeService (in one transaction) checks an active original Sale payment, non-refunded and unused physical Return, new inventory availability, creates **one** new linked Sale using a deterministic derived idempotency key, and records Exchange with financial difference:
`returnedValue,newSaleTotal,creditApplied,additionalPaymentDue,refundDue`.
UI never calculates authoritative financial amounts. ExchangeStatus only PENDING_SETTLEMENT or COMPLETED, taken from backend; zero-difference may automatically be COMPLETED.
The Exchange Detail shows original/new sale, linked Return, all five monetary fields and *read-only* settlement history.
**No `POST /api/exchanges/{id}/settlements` or refunds in F09.** Those debit/credit CASH/BANK and belong to F10.

Creation uses the same safe-key sessionStorage pattern as physical returns. If result is unknown, do not start a second new Sale. No global list/API is fabricated. Further benefit requires a **separate backend ticket** for GET /api/exchanges list.

## Defects: statuses and real inventory effects
`GET /api/defects?status=OPEN|WAITING_PARTS|RESOLVED|WRITTEN_OFF&page=<n>&size=20`, `GET /api/defects/{id}` (DEFECTS_READ).
Backend record fields: `id,warehouseId,productVariantId,supplierId,purchaseReceiptItemId,quantity,status,description,resolutionNote,createdBy,createdAt,resolvedBy,resolvedAt`. No invented SKU/category/name. When CATALOG_READ exists, detail may read the variant name; list remains N+1 free and uses the truthful UUID instead. Supplier and receipt item references are optional; manual UUID input is accepted, backend validates any linked receipt against the warehouse/variant/supplier. Never infer purchaseReceiptItemId from ReturnItem.id.

`POST /api/defects` (DEFECTS_MANAGE) blocks the existing on-hand quantity; never creates RETURN_IN, never increases onHand. No duplicate block on previously BLOCKED returns. Request: `{warehouseId,productVariantId,supplierId|null,purchaseReceiptItemId|null,quantity,description}`; description 1..4000, quantity positive integer.
`POST /api/defects/{id}/wait-for-parts` (DEFECTS_MANAGE) OPEN→WAITING_PARTS (remains blocked).
`POST /api/defects/{id}/resolve` `{resolutionNote}` (DEFECTS_MANAGE) OPEN/WAITING_PARTS→RESOLVED, backend unblocks.
`POST /api/defects/{id}/write-off` `{resolutionNote}` (**DEFECTS_WRITE_OFF**) OPEN/WAITING_PARTS→WRITTEN_OFF. Explicit destructive confirmation includes variant, warehouse, count and description. Backend removes blocked count, performs WRITE_OFF and FIFO cost posting. Frontend cannot call inventory unblock/stock movements and never optimistically changes quantities.

Neither defect creation nor write-off has an idempotencyKey field. A safe pending-operation marker is persisted before mutation, and a network/409/5xx uncertainty blocks blind retries; manual reconciliation of backend state and inventory movements is required. Only IDs and random operation marker are persisted, no problem description or resolution note. Write-off does not derive authority from DEFECTS_MANAGE.

## Permissions / security
All permissions are **effective permissions**, not role names:
| Operation | Required |
|---|---|
| Return list/detail | RETURNS_READ |
| Return creation | RETURNS_CREATE (+ SALES_READ, RETURNS_READ for the safe item-history picker) |
| Sales names/eligibility | SALES_READ |
| Warehouse dropdown | INVENTORY_READ |
| Exchange view/settlements | EXCHANGES_READ |
| Exchange create | EXCHANGES_CREATE (may create without EXCHANGES_READ) |
| Defect list/detail | DEFECTS_READ |
| Defect create/wait/resolve | DEFECTS_MANAGE |
| Defect FIFO write-off | DEFECTS_WRITE_OFF |
| Product details | CATALOG_READ |

Without source read permission, the form shows a clear limitation and does not query protected endpoints. Backend authorization remains the final security boundary. Errors 401/403/404/409 and network use existing ApiClientError and page conventions; no stack traces, cost prices, purchase margins, or optimistic money writes.

## UX, query invalidation, regression
Desktop tables + mobile cards; shared PageHeader, PageTabs, PurchasePager, variants, warehouses; readable status and reason. Targeted invalidation after success: returns lists/history, sales lists, inventory overview/balance/movements; exchange and sales; defects lists/detail and inventory. No global query cache purge.

Tests: Vitest/RTL new F09 scenarios plus all existing F01–F08 tests. GitHub Actions frontend lint/typecheck/Vitest/build, Docker Compose smoke. Selective real PostgreSQL Testcontainers backend suite in `.github/workflows/f09-returns-integration.yml`: ReturnApiTests, ReturnConcurrencyTests, ReturnTests, ExchangeWorkflowTests, ExchangeCreationTests, ExchangeSettlementTests, DefectTests and DefectOriginTests; these are existing tests (unmodified), not a full frontend browser-to-PostgreSQL E2E. Check their results for exact PR head before calling verification complete.

## Explicit exclusions / limitations
No GET list for exchanges, no exchange settlement/refund/client payment POST, no supplier return to China, no external courier/WhatsApp/SMS integrations, no photos, no additional defect statuses, no auto-created Defect on BLOCKED Return, no inventory moves/unblock in UI, no backend migrations. Full isolated browser-to-real-backend E2E should be separately run before production use. Disabled operations when a transaction outcome cannot be determined are intentionally conservative.
