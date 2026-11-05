# ChairX: current architecture guide and high-risk behavior

## Purpose
ChairX is a small-business ERP/CRM for an office-chair retailer in Kyrgyzstan. Core flows span catalog, stock and FIFO, suppliers/purchases/receipts, customers, sales, delivery, returns/refunds, expenses, finance and daily close, staff roles and reporting.

This is **curated explanatory documentation**, not automatically inferred. Verify against current Java, schema and integration tests whenever changing behavior.

## Backend architecture
Modular monolith (Java 25 / Spring Boot 4.1.1), PostgreSQL 17 with Flyway, services using Spring JDBC/JdbcClient, transaction boundaries around financial and inventory mutations. Frontend: React 19 / TypeScript / TanStack Query / Vite. No assumed event bus or independent microservices.

## Main entity relationships
- Product -> ProductVariant -> InventoryBalance per warehouse; StockMovement and FIFO cost layers/allocations record stock and valuation changes.
- Supplier -> Purchase -> PurchaseItem -> PurchaseReceipt (partial receiving) -> warehouse/FIFO layer.
- Customer -> Sale -> SaleItem; Sale connects to Delivery and Payment; a returned sale item connects to Return/Refund. Money and operational fulfillment are separate facts.
- FinanceAccount (CASH/BANK) -> FinanceMovement ledger; purchases, customer payments, refunds and expenses post specific signed movements. DailyClosing snapshots reconcile business dates.
- AppUser <-> SecurityRole <-> Permission; audits track relevant authorization/business mutations.

These are **conceptual relationships**. For exact table names, keys, cardinalities and nullable constraints read all Flyway migrations and generated/database.md; for DTOs read Java api records.

## High-risk rules to preserve
1. **Inventory:** availability is on-hand minus reserved minus blocked. Confirmation/reservation, physical dispatch/fulfillment and actual delivery are different states. No negative availability or double-restock.
2. **FIFO:** consume cost layers exactly once; prevent double cost restoration on returns and failed deliveries. Do not expose confidential FIFO costs in ordinary warehouse API DTOs.
3. **Money:** CASH/BANK change only through approved finance posting/transfer operations; no silent balances changes. A payment is not automatically created when delivery completes. Refunds and cancellations require appropriate financial treatment.
4. **Daily close:** Asia/Bishkek reporting date, immutable historical snapshots except authorized versioned corrections; individual employee close is not company closing and must not mutate shared balances.
5. **Security:** HTTP authentication + CSRF for writes; method+path grants and deny-by-default in SecurityConfig. A button hidden in the UI does not authorize/deny the API. Admin actions require actual system-admin role plus permissions.
6. **Retries:** respect idempotency identifiers and expected-version guards. Duplicate HTTP retries must not create duplicate money or stock movements.
7. **Returns model:** current product decision removed the user-facing Exchange workflow; return goods/refund and create a separate new Sale. Legacy migration/compatibility tables do not imply active Exchange API behavior.

## Find source and evidence
- Java domain records and DTO types: generated/models.md
- Current controller routes: generated/endpoints.md
- Service operations: generated/services.md
- SQL relationships: generated/database.md
- UI sections: generated/frontend.md
- Transactional tests: backend/src/test/
- Browser/integration scenarios: scripts/f11-*.py, scripts/f13-admin-e2e.py and scripts/studio-*.py

## Documentation stewardship
When a PR changes state transitions, posted money, FIFO semantics, authorization rules or a significant workflow, its author must update this curated guide with the new verified rule and supporting code/tests. Generated data does not replace business explanation. Update any relevant legacy documentation rather than silently inheriting outdated statements.
