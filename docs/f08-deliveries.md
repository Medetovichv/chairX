# ChairX F08 — Delivery Management & Logistics

## Scope and architecture

F08 is frontend-only in \`frontend/src/features/deliveries/\`, implemented on
top of the existing Spring \`DeliveryController\`, \`DeliveryService\`,
\`DeliveryRepository\` and real DTO records. **No backend, database migration,
payment API, courier assignment, GPS, or external carrier integration.**

Pages:
- \`/deliveries\` — responsive server-paginated delivery overview (20/page).
- \`/deliveries/new\` — delivery creation for confirmed sales.
- \`/deliveries/:deliveryId\` — real detail, timestamp timeline, lifecycle actions.

Existing \`AuthProvider\`, \`apiClient\`, TanStack Query, navigation, purchase
pager, inventory warehouse API and confirmation dialog are reused. F07 sale
detail adds one link for eligible delivery orders. All inventory, sale
fulfillment, reserves, return cost layers and audit writes are owned by backend.

## Real API contracts

| Operation | Endpoint | Required permission |
|---|---|---|
| List | GET /api/deliveries | DELIVERIES_READ |
| Detail | GET /api/deliveries/{id} | DELIVERIES_READ |
| Create | POST /api/deliveries | DELIVERIES_MANAGE |
| Date | PUT /api/deliveries/{id}/planned-date | DELIVERIES_MANAGE |
| Dispatch | POST /api/deliveries/{id}/dispatch | DELIVERIES_MANAGE |
| Deliver | POST /api/deliveries/{id}/deliver | DELIVERIES_MANAGE |
| Fail | POST /api/deliveries/{id}/fail | DELIVERIES_MANAGE |
| Return | POST /api/deliveries/{id}/return-to-warehouse | DELIVERIES_MANAGE |
| Cancel | POST /api/deliveries/{id}/cancel | **SALES_CANCEL** |

The route /deliveries/new requires both DELIVERIES_READ and DELIVERIES_MANAGE:
without read, the UI does not bypass protected endpoints. Creation with
DELIVERIES_MANAGE and without SALES_READ is possible by entering a known UUID
as the backend checks the linked sale; with SALES_READ the UI offers an
actual paginated confirmed-sale picker. Warehouse selection requires
INVENTORY_READ (existing GET /api/warehouses, active only). SALES_READ
is optional for linking to /sales/:saleId and for the sale picker.

Backend DTOs:
- \`DeliveryPageResponse\`: \`items,page,size,total\`.
- \`DeliverySummary\`: \`id,saleId,status,recipientName,cityRegion,createdAt,
  plannedDeliveryDate,saleNumber,recipientPhone,address,fulfillmentType,deliveredAt\`.
- \`DeliveryResponse\`: \`id,saleId,status,recipientName,recipientPhone,address,
  cityRegion,deliveryCost,carrierName,trackingNumber,comment,createdBy,createdAt,
  dispatchedBy,dispatchedAt,deliveredBy,deliveredAt,failedBy,failedAt,failureReason,
  cancelledBy,cancelledAt,returnedToWarehouseBy,returnedToWarehouseAt,
  returnWarehouseId,plannedDeliveryDate\`.
- Create request fields \`saleId,recipientName,recipientPhone,address,cityRegion,
  deliveryCost,carrierName,trackingNumber,comment,plannedDeliveryDate\`.
- Fail payload \`{reason}\`; return \`{warehouseId}\`; date \`{plannedDeliveryDate}\`.

## Filters / dates / pagination

Backend \`GET /api/deliveries\` accepts \`status,from,to,plannedFrom,
plannedTo,cityRegion,page,size\`. Every filter and page contributes to the
TanStack Query key; the UI never paginates an entire backend dataset locally.
- All: no status/planned date filter.
- Today: \`plannedFrom=plannedTo\` at the current **Asia/Bishkek** LocalDate.
- Upcoming: \`plannedFrom=tomorrow\` in Asia/Bishkek.
- Ready, in transit, delivered, failed, cancelled: corresponding real status.
- Created \`from/to\` filters are distinct from planned \`plannedFrom/plannedTo\`.
- Region \`cityRegion\` is debounced; changing any filter resets page to 0.
- There is **no invented saleNumber search** on DeliveryController.
- Summary already supplies sale number, phone, address and fulfillment type:
  no sale GET per list row, no N+1 API queries.

## Business workflow and transition matrix

| Original | Action | Result | Inventory / sale |
|---|---|---|---|
| CONFIRMED sale + CITY_DELIVERY or REGION_DELIVERY | Create | READY | Sale stays CONFIRMED; no stock movement or payment |
| READY | Dispatch | IN_TRANSIT | Backend atomically releases reserve, posts SALE_OUT and sets Sale FULFILLED |
| IN_TRANSIT | Deliver | DELIVERED | Records recipient handoff; **no additional SALE_OUT** |
| IN_TRANSIT | Fail, with reason | FAILED | Records reason; **no RETURN_IN** |
| FAILED (no return recorded) | Return to active warehouse | Still FAILED | Backend RETURN_IN + cost layers; Sale stays FULFILLED |
| READY | Cancel (SALES_CANCEL) | CANCELLED | Backend cancels Sale + releases reserve |
| READY / IN_TRANSIT | Set or clear planned date | Status unchanged | Does not change delivery financials |
| Delivered/Failed/Cancelled | Update planned date | Forbidden | Backend rejects |
| Others | Unauthorized transition | Forbidden | No optimistic local posting |

Backend idempotent responses may return the prior state for dispatch,
deliver, fail, cancel and return; frontend never assumes a second POST is
safe solely because the first response was lost. A fresh GET is used to
reconcile status and return fields. Concurrent changes between preflight
GET and POST still require backend transaction/lock enforcement.

## Creation validation and duplicate prevention

Phone (string, preserves +) required/max 50; address required/max 500;
recipient name optional/max 200; region, carrier and tracking optional/max
200; comment max 2000; delivery cost **integer, non-negative som** up to
19 digits, sent as a decimal string (not float); planned date optional
ISO LocalDate. Creation does **not** book a customer payment.

Only backend-confirmed sale, CITY_DELIVERY or REGION_DELIVERY is valid;
SELF_PICKUP is forbidden; backend one-delivery-per-sale uniqueness
also protects concurrent creation. The F07 link appears only on eligible
confirmed delivery sales, when the delivery role is available. If the
known SaleSummary already indicates delivery exists, link to the delivery
list rather than create. There is **no GET by saleId** exposed by
DeliveryController: opening a direct delivery detail requires an actual
delivery UUID. We do not invent it or bulk-query every delivery.

**Create has no idempotencyKey.** The exact request is persisted in
user-scoped, tab-scoped \`sessionStorage\` before POST. After a network,
HTTP 5xx or duplicate 409 result, creation remains blocked until manual
reconciliation in the deliveries list; it does **not auto-replay POST**.
Tab closure/storage cleanup loses recovery state. The backend unique
saleId constraint still prevents two delivery rows, but does not return
the original success body on duplicate POST.

## Daily operations / UX / safety

Responsive table/card listing and large action buttons. Delivery detail
prioritizes recipient, copyable phone/address, city, schedule, carrier,
tracking, failure reason, actual warehouse return timestamp and actor
details. Timeline uses **only** real DTO timestamp/actor fields;
no fabricated audit API or intermediate statuses. FAILED+returned is
shown as "Товар возвращён на склад", not status RETURNED. No automatic
refund, client payment, or courier integrations.

Mutation preflight GET refuses stale lifecycle changes. Confirmation
describes stock/sale impacts. During pending/ambiguous responses controls
are disabled; fresh detail reconciliation is required. A failed status
does not by itself increment stock. No UI optimistic inventory or sale
status updates and **no secondary sale/fulfill call** after dispatch.

On confirmed backend success query invalidation covers delivery list/detail,
sales list/Kanban/sale detail, inventory overview, balances and movements.
Effective permission flags rather than role names support custom P23 roles.
Without DELIVERIES_READ no list/detail queries are issued; without SALES_READ
no sales queries; without INVENTORY_READ no warehouse query.

## Test and integration policy

Vitest / RTL cover list, responsive display, date / region / status filters,
backend pagination, draft creation payload and validation, status transitions,
date updates, failed vs returned distinctions, permission isolation,
unknown results, and cache invalidation. Legacy F01–F07 tests must still pass.

Run in \`frontend/\`: \`npm ci\`, \`npm run lint\`, \`npm run typecheck\`,
\`npm run test\`, \`npm run build\`. CI links/results recorded in PR #28.

A full **real-backend + PostgreSQL end-to-end sale → inventory reservation →
delivery → dispatch → SALE_OUT → deliver / fail → RETURN_IN** must be
executed against isolated test data. Unless explicitly proven in an
integration test, Vitest mocked HTTP and the generic Docker smoke are
**not** proof of financial/stock correctness. Never touch production data.

## Deliberate exclusions

No new backend endpoints, schema changes, client payment registration,
financial refunds, driver/GPS/route integrations, SMS, WhatsApp, additional
delivery statuses, courier assignment, manual inventory modification,
price/cost calculations, or changes to backend fulfillment rules.
