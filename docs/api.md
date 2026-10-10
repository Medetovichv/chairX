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
