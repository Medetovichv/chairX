# ChairX backend REST API (P20)

All routes are relative to `/api`. Current middleware requires authentication and CSRF for state changes (see `GET /api/csrf`). Detailed staff permission policy is **P17-dependent**; do not deploy P20 without integrating and verifying it.

## Main screen routes

| Screen | Request | Data / notes |
|---|---|---|
| Products | `GET /products?page=0&size=20`, `GET /products/{id}` | Includes variant subroutes and add/update/activate workflows |
| Suppliers / Customers / Warehouses | Existing `/suppliers`, `/customers`, `/warehouses` | Use existing controllers and DTOs; no new CRUD |
| Warehouse list | `GET /inventory/balances?warehouseId=UUID&page=0&size=20` | `items` each with warehouseId, productVariantId, onHand, reserved, blocked, **available**; `page,size,total`. Only persisted positive/changed rows listed |
| Stock per variant | `GET /inventory/balances/{warehouseId}/{variantId}` | Also returns zero counters for valid pair with no balance row; no mutation |
| Stock movements | `GET /inventory/movements?warehouseId=UUID&variantId=UUID&page=0&size=20` | Returns existing `StockMovementPage` (`totalElements`), chronological |
| Purchase list/detail | `GET /purchases?page=0&size=20`; `GET /purchases/{id}` | Purchase, items, cargo and receipt status |
| Purchase receipts | `POST /purchases/{id}/receipts`; `GET /purchases/{id}/receipts`; `GET /purchases/{id}/receipts/{receiptId}` | Existing partial receipt DTO/unique key |
| **Purchase payments** | **`POST /purchases/{purchaseId}/payments`**; **`GET /purchases/{purchaseId}/payments`** | New P20-A, see below |
| Sale list | **`GET /sales?page=0&size=20&status=CONFIRMED&from=YYYY-MM-DD&to=YYYY-MM-DD&number=TEXT`** | All filters optional. Dates inclusive by **Asia/Bishkek** business date. Compact saleNumber/customer/total/status/fulfillmentType |
| Sale detail/issue | `GET /sales/{id}`; `POST /sales`; `POST /sales/{id}/fulfill`; `POST /sales/{id}/cancel` | Existing DTOs and business rules |
| Sale payment | `POST /payments`, `GET /payments/{id}` | `CreatePaymentRequest` now has optional `channel`; method CASH or TRANSFER still required |
| Delivery list | **`GET /deliveries?status=READY&page=0&size=20&from=YYYY-MM-DD&to=YYYY-MM-DD`** | All filters optional; dates inclusive by Bishkek business date |
| Delivery workflow | `POST /deliveries`, `GET /deliveries/{id}`, `POST /deliveries/{id}/dispatch`, `/deliver`, `/fail`, `/cancel`, `/return-to-warehouse` | Existing workflow; failing delivery does not restock automatically |
| Return list | **`GET /returns?saleId=UUID&page=0&size=20`** | Optional saleId; lightweight ReturnSummary |
| Return detail/create | `GET /returns/{id}`, `POST /returns` | Existing return idempotency; cannot return undelivered goods |
| Refund / Exchange | Existing `/refunds`, `/exchanges` routes | Keep original linked refund/settlement contracts |
| Defects | **`POST /defects`, `GET /defects/{id}`, `GET /defects?status=OPEN&page=0&size=20`, `POST /defects/{id}/wait-for-parts`, `/resolve`, `/write-off`** | New P20-C facade over DefectService |
| Expenses | Existing `/expenses` | POST requires UUID `idempotencyKey` |
| CASH/BANK and closing | `GET /finance/accounts`, `POST /finance/opening-balances`, `POST /finance/transfers`, `/finance/closings` | Existing P16/P19 services, protected financial operations |
| Cash flow | `GET /finance/cash-flow?from=YYYY-MM-DD&to=YYYY-MM-DD` | Half-open [from,to) business days; new `purchasePayments` negative-cash-out breakdown |
| Staff | Existing `/admin` routes | Must be revalidated after P17 integration |

### POST /purchases/{purchaseId}/payments

```json
{
  "idempotencyKey": "d6c7d8b0-b997-42e1-962e-0682b41e493e",
  "paymentKind": "SUPPLIER",
  "account": "BANK",
  "amount": 30000,
  "reference": "MBANK-12345",
  "comment": "Аванс поставщику"
}
```

Required: `idempotencyKey` UUID, `paymentKind` SUPPLIER or CARGO, `account` CASH or BANK, positive **integer** `amount`. The optional reference <=200 chars, comment <=1000 chars; whitespace trimmed. Response 201 + Location and `PurchasePaymentResponse` (id, purchaseId, paymentKind, account, amount, idempotencyKey, reference, comment, createdBy, createdAt).

GET returns:
```json
{
  "purchaseId":"...",
  "supplierTotal":50000,
  "cargoTotal":10000,
  "paidSupplier":30000,
  "paidCargo":0,
  "remainingSupplier":20000,
  "remainingCargo":10000,
  "payments":[]
}
```
Actual list contains saved payment DTOs. For undefined cargo, `cargoTotal` and `remainingCargo` are null. Successful equal-idempotency replay returns the same payment document; no additional journal movement. Different body with reused key → 409 `PURCHASE_PAYMENT_IDEMPOTENCY_CONFLICT`. Exceeding supplier/cargo caps → 409 `PURCHASE_PAYMENT_EXCEEDS_TOTAL`. Cancellation of paid purchase → 409 `PAID_PURCHASE_CANNOT_CANCEL`. Insufficient finance balance, uninitialized account or closed day → HTTP 409 and transaction rollback.

### POST /payments (sale payment channel)

Use the preexisting sale payment request plus **optional** `channel`:
```json
{
  "saleId":"00000000-0000-0000-0000-000000000001",
  "method":"TRANSFER",
  "channel":"MBANK",
  "reference":"MBANK-123",
  "comment":"Деньги зачислены"
}
```
`method=CASH` permits only channel CASH; `method=TRANSFER` permits `BANK_TRANSFER`, `MBANK`, `BANK_INSTALLMENT`. Missing channel maps to CASH or BANK_TRANSFER (historical compatibility); a mismatched pair → HTTP 409 `PAYMENT_CHANNEL_MISMATCH`. The request records PAID immediately, so submit BANK_INSTALLMENT **only after actual payout to business**. Historical payment rows are not rewritten; `channel` on response may use the generic fallback.

### Defect DTO examples

`POST /defects`:
```json
{"warehouseId":"UUID","productVariantId":"UUID","supplierId":null,"purchaseReceiptItemId":null,"quantity":2,"description":"Повреждён механизм"}
```
Resolve or write off: `{"resolutionNote":"Ремонт выполнен"}`. Response reuses the Defect record including status and receipt origin. Duplicate closing → HTTP 409. Missing defect → HTTP 404.

## Query parameters and errors

Page is zero-based, `size` 1–100. Invalid page/date ranges return HTTP 400 `INVALID_QUERY`. Validation of request bodies uses HTTP 400 `VALIDATION_ERROR`. Business-state conflicts are HTTP 409 with the specific domain code; missing object uses HTTP 404. Duplicate-operation UUID keys never permit different payloads. No query endpoint mutates stock or finance journals.

**Compatibility:** CashFlow adds the field `purchasePayments` and changes `totalOut` to include purchase payouts. Clients of prior response DTOs must accommodate the extra field. No frontend is included in P20.


## P22 — Operational lists and daily sales (2026-10-10)

All timestamps are UTC instants; business-date parameters are interpreted in `Asia/Bishkek`.
Lists are independent of financial closings. Unless noted, `page` is zero-based,
`size` is 1–100 and invalid filters return HTTP 400.

| Endpoint | Permission | Purpose |
|---|---|---|
| `POST /api/sales/drafts` | SALES_CREATE | Create DRAFT without stock reservation |
| `PUT /api/sales/{id}/draft` | SALES_UPDATE | Replace DRAFT fields/items, no stock reservation |
| `POST /api/sales/{id}/confirm` | SALES_UPDATE | Atomically validate and reserve inventory |
| `POST /api/sales/{id}/cancel` | SALES_CANCEL | Cancel DRAFT or existing confirmed pickup |
| `GET /api/sales` | SALES_READ | Paginated list with quantity, products, payment/delivery status |
| `GET /api/sales/customer/{customerId}` | SALES_READ + CUSTOMERS_READ | Paginated client-specific sales |
| `GET /api/customers/overview` | SALES_READ + CUSTOMERS_READ | Paginated contacts with order count and most recent sale |
| `GET /api/deliveries` | DELIVERIES_READ | Paginated deliveries, status/date/region |
| `PUT /api/deliveries/{id}/planned-date` | DELIVERIES_MANAGE | Change plan in READY / IN_TRANSIT only |
| `GET /api/inventory/overview` | INVENTORY_READ | Paginated stock across all or a selected warehouse |
| `GET /api/purchases/overview` | PURCHASE_READ | Paginated quantities, cargo and receipts |
| `GET /api/finance/closings/{date}/sales` | DAILY_CLOSING_READ or FINANCE_READ | Completed sales and immutable snapshot |

**Sale workflow**: `POST /api/sales` remains an immediate CONFIRMED sale with its
existing idempotency key and inventory reservation. Use the *new* endpoint for work
lasting multiple days. Draft creation supports `customerId=null`,
`fulfillmentType=null`, `items=[]`. Each create requires a UUID
`idempotencyKey`; same key + identical payload returns the original order,
while different payload returns 409. Draft edit is a full replacement, including
empty fields; confirmation requires fulfillmentType, >=1 valid line, active
references and available stock. Confirmation commits or rolls back as a unit.
Repeated confirmation is safe. Draft cancellation never touches inventory.

Example draft request:
```json
{
  "idempotencyKey": "06fa4001-e9e2-4e72-910b-6a63bf2fb39c",
  "customerId": null, "fulfillmentType": null,
  "items": [], "comment": "Клиент выбирает цвет"
}
```

Example draft update:
```json
{
  "customerId": "00000000-0000-0000-0000-000000000001",
  "fulfillmentType": "CITY_DELIVERY",
  "items": [{"productVariantId": "00000000-0000-0000-0000-000000000002",
             "warehouseId": "00000000-0000-0000-0000-000000000003",
             "quantity": 2, "unitSalePrice": 8500}],
  "comment": "Доставка завтра"
}
```

**Sales query**: `GET /api/sales?page=0&size=20&status=DRAFT&from=2026-10-01&to=2026-10-31&number=SALE-`.
The existing page and fields remain; `SaleSummary` adds `quantity`,
`products` (human-readable text), `paymentStatus` (`UNPAID`, `PARTIAL`,
`PAID`), `deliveryStatus`, `plannedDeliveryDate`.
These fields come from joined SQL (no per-row fetch), do not depend on
closed-day reports and include old DRAFT orders. Null customer names are
returned as JSON null. `GET /api/sales/customer/{customerId}?page=0&size=20`
returns only that customer's order history, requiring both permissions.

**Customers**: `GET /api/customers/overview?query=0555&page=0&size=20` searches name, phone, secondary/WhatsApp numbers and Instagram handle; returns `orderCount`, `lastSaleNumber`, and `lastOrderAt` for authorized readers. This combined projection requires both `CUSTOMERS_READ` and `SALES_READ`.

`CreateCustomerRequest.fullName` / `UpdateCustomerRequest.fullName`
may be JSON null or blank (stored as null). At least one contact is needed:
`phone`, `secondaryPhone`, `whatsappPhone` or `instagramUsername`.
No dummy "Без имени" is persisted. Existing name-bearing requests still
work; phone numbers remain non-unique. Example:
```json
{"fullName": null, "phone": "0555123456", "secondaryPhone": null,
 "whatsappPhone": null, "instagramUsername": null,
 "address": null, "cityRegion": "Бишкек", "comment": null}
```

**Deliveries**: creating a delivery accepts `recipientName: null` while
required phone/address validation stays in place. `plannedDeliveryDate`
is an optional ISO date (e.g. `"2026-10-15"`); existing deliveries report null.
For rescheduling, `PUT /api/deliveries/{id}/planned-date`:
```json
{"plannedDeliveryDate": "2026-10-17"}
```
A null date clears the plan, but only in READY or IN_TRANSIT. The update
is audited. New list filters `plannedFrom`, `plannedTo`, `cityRegion`
are distinct from original `from` and `to` (which filter creation date).
`DeliverySummary` adds sale number, recipient phone/address, fulfillment
type and actual `deliveredAt`.

**Stock overview**: `GET /api/inventory/overview?warehouseId=<UUID>&model=...&variation=...&includeZero=false&onlyAvailable=true&page=0&size=20`.
`warehouseId` optional means all warehouses. Each result has onHand,
reserved, blocked, available and `warehouses[]` with real UUID and code,
so UI can draw HOME/OFFICE columns without hardcoded UUIDs. No mutation of
stock or FIFO happens in GETs.

**Purchase overview**: `GET /api/purchases/overview?status=PARTIALLY_RECEIVED&supplierId=<UUID>&from=2026-10-01&to=2026-10-31&page=0&size=20`.
Each row includes `itemCount`, `orderedQuantity`, `receivedQuantity`,
`remainingQuantity`, `goodsCost`, `cargoCost`, `totalCost`,
`lastReceiptAt`, supplier name, date and existing status. If cargo is not yet known, both `cargoCost` and `totalCost` are null (not a fabricated 0); `goodsCost` remains available. No invented
shipment statuses or expected-arrival dates.

**Daily completed sales**: `GET /api/finance/closings/2026-10-10/sales`.
Response contains:
```json
{
 "businessDate": "2026-10-10", "closed": true,
 "snapshotAvailable": true, "preliminary": false,
 "items": [{"saleId":"...","saleNumber":"...","customerName":null,
   "phone":"0555123456","products":"Office chair / Black ×2",
   "quantity":2,"address":null,"fulfillmentType":"SELF_PICKUP",
   "completedAt":"2026-10-10T08:30:00Z",
   "total":17000,"paymentStatus":"PAID"}],
 "totals":{"orders":1,"chairs":2,"value":17000},
 "lateCompletionCount":0
}
```
This is an operational result, **not actual CASH/BANK or daily revenue
received**. For self-pickup, completion is Sale.FULFILLED at fulfilledAt.
For CITY_DELIVERY and REGION_DELIVERY, completion is
Delivery.DELIVERED at deliveredAt; Sale.FULFILLED during dispatch is **not**
enough. Before closure, values are preliminary. P21 closing atomically captures
an immutable snapshot; late completions on a closed day are diagnosed using
`lateCompletionCount`, never silently added retroactively. Historic P21
closings without a snapshot return `snapshotAvailable=false`, null totals,
and do **not** invent a historical result. Finance postings remain solely under
existing FinancePostingService.


## P23 endpoints — roles, permissions and receiving

See [P23 role management](p23-role-management.md) for request/response examples,
admin-only checks, optimistic locking (HTTP 409) and default grants.

| Method | Endpoint | Authorization |
|---|---|---|
| GET | `/api/auth/me` | Authenticated active employee (F02) |
| GET | `/api/admin/roles`, `/api/admin/roles/{id}` | Active system ADMIN + ROLES_READ |
| POST | `/api/admin/roles` | Active system ADMIN + ROLES_CREATE; CSRF |
| PUT | `/api/admin/roles/{id}` | Active system ADMIN + ROLES_UPDATE; CSRF |
| GET | `/api/admin/permissions` | Active system ADMIN + ROLES_READ |
| GET | `/api/purchases/receiving` | INVENTORY_RECEIVE |
| GET | `/api/purchases/{id}/receiving-summary` | INVENTORY_RECEIVE |
| PUT | `/api/sales/{id}/draft` | SALES_DRAFT_MANAGE |
| POST | `/api/sales/{id}/confirm` | SALES_DRAFT_MANAGE; CSRF |

Existing `POST /api/sales/{id}/fulfill` continues to require SALES_UPDATE.
Role list responses retain `id`, `code` and `name`, with added
`systemRole`, `permissions`, `assignedUsersCount` and `version`.

| GET | `/api/admin/audit?targetId=<uuid>&page=0&size=20` | Active system ADMIN + ROLES_READ |
