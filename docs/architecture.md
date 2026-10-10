# ChairX — backend architecture (Package 20)

ChairX is a **modular monolith**: Spring Boot / Java 25, PostgreSQL 17, Flyway, Spring JDBC/JdbcClient, transactions, REST DTOs, Testcontainers. No JPA rewrite, message bus, distributed workflow, multi-currency or hidden async transfers. Monetary amounts are Kyrgyz soms (integer in financial accounts); costs can retain cents for FIFO allocation.

| Module | Responsibility and main entities | Application boundary / storage | Connected rules |
|---|---|---|---|
| Catalog (product) | Products and variants | ProductService / ProductVariantService; `products`, `product_variants` | Items reference active variants when created; historic variant IDs retained |
| Supplier & Customer | Active partner master data | SupplierService / CustomerService; `suppliers`, `customers` | Supplier on purchase, customer on sale |
| Warehouse | HOME and OFFICE reference stores | WarehouseService; `warehouses` | All stock operations name origin/destination warehouse |
| Purchase | Purchase, ordered items, partial receipts, allocated cargo | PurchaseService and ReceivePurchase; `purchases`, `purchase_items`, `purchase_receipts`, `purchase_receipt_items` | Confirm before receipt, receipt <= ordered, cargo defined before first receipt; independently payable |
| Purchase payments (P20-A) | Supplier and cargo cash expenditures | PurchasePaymentService, PurchasePaymentRepository; `purchase_payments` | Locked Purchase, bounded partial payments, unique retry key, journal-linked one-time posting, no cancellation after pay |
| Inventory | Available = onHand - reserved - blocked, movement history | InventoryService/InventoryRepository; `inventory_balances`, `stock_movements` | Sale reserves, fulfillment consumes, receipt increases; GET never creates stock movement |
| FIFO | Valued purchase layers and consumption/restoration | InventoryCostService/InventoryCostPostingService; `inventory_cost_layers`, `inventory_cost_allocations`, `inventory_cost_restorations`, `inventory_cost_write_offs` | Cost of purchase includes allocated cargo; payouts do not alter FIFO |
| Sale | Sale, sale items, source warehouse, fulfillment | SaleService; `sales`, `sale_items` | Cannot sell unavailable units, unique creation key, sale status + payment guard |
| Delivery | READY/IN_TRANSIT/DELIVERED/FAILED, physical failed receipt | DeliveryService; `deliveries` | Ordinary customer returns only on DELIVERED, failed return uses dedicated warehouse receipt |
| Payment | Paid/cancelled sale settlement, CASH or BANK | PaymentService/PaymentBalanceService; `payments` | P20-B channel CASH/BANK_TRANSFER/MBANK/BANK_INSTALLMENT; no PAID until funds received |
| Return | Partial customer item return | ReturnService; `returns`, `return_items` | Delivered/specified self-pickup only, bounded quantities, FIFO restoration guard, idempotent key |
| Refund | Customer repayment | RefundService; `refunds` | Amount limited by returned sale goods; financial debit |
| Exchange | Swap to new sale, settlement of price differences | ExchangeService/ExchangeSettlementService; `exchanges`, `exchange_settlements` | Do not double charge; signed settlement debits/credits |
| Expense | Operational cash outgo | ExpenseService; `expenses` | Idempotent signed finance postings; expense date distinct from posting time |
| Finance | CASH/BANK balances, transfers, closing, cash flow and diagnosis | FinancePostingService/FinanceTransferService/DailyClosingService/CashFlowService; `finance_accounts`, `finance_movements`, `finance_transfers`, `finance_daily_closings`, `finance_document_posting_issues` | All money changes via journal; close serializes account rows; report based on posting-time Asia/Bishkek |
| Defect | OPEN/WAITING_PARTS/RESOLVED/WRITTEN_OFF | DefectService/DefectRepository; `defects` | Block on open, unblock on resolution or valued write-off; receipt-item origin optional |
| Security (P17 separate) | Employee accounts, roles, permissions and auth | Existing security package and user/role tables | P20 **does not change** security. Validate new routes against merged P17 policies |
| Audit | Trace of critical business mutations | AuditService; `audit_entries` | Keep document changes in their transaction |

### Transaction and locking contracts

PurchasePayment writes acquire Purchase `FOR UPDATE`, then post via FinancePostingService, which locks the cash/bank account; this serializes payout ceilings and money movements. Purchase cancellation and cargo edits lock the same Purchase. Receipt and payment are **separate**, and confirmation or receipt is never predicated on complete payment.

Sale holds sale locks for payment/cancellation/returns; Inventory locks balance by warehouse+variant, with separate FIFO cost journals. Failed-delivery physical warehouse receipt cannot be replaced by a customer return. Finance daily closing is an immutable snapshot and prevents later posting into the closed current business day.

### P17 integration boundary

The P20 controllers rely on **current** authentication middleware but introduce no new roles. Before exposing them to staff after integration, P17 must review authorization for purchase payments, inventory and financial lists, delivery, returns, and defects. P20 is **not** production-ready before combined security and deployment testing.
