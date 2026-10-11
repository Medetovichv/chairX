# ChairX — isolated local demo database (work in progress)

## Safety boundary

The usual `docker compose up -d --build` continues using **chairx** and its original PostgreSQL volume. The demo overlay uses a separate Compose project named **chairx-demo**, separate database **chairx_demo**, and separate named volume **chairx_demo_postgres_data**.

Never enable demo fixtures on a production backend. Never run destructive SQL against the normal database. Do not implement this via Flyway production migrations, `data.sql`, frontend mock data or direct inserts into finance/inventory/closing ledgers.

## Start / stop / reset

Ensure the ordinary stack is stopped first (both stacks use localhost ports 5432/8080/5173):

```sh
docker compose down
docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d --build --wait
```

The demo volume is persistent. Rebuilding and restarting does **not** wipe records. This is intentional.

To stop without deleting demo data:

```sh
docker compose -f docker-compose.yml -f docker-compose.demo.yml down
```

To explicitly remove **only demo** volumes and regenerate an empty demo database:

```sh
docker compose -f docker-compose.yml -f docker-compose.demo.yml down --volumes
docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d --build --wait
```

The separate Compose project name ensures this does not target the ordinary `chairx` project. Still inspect `docker compose ... config` before a reset. Do not use `docker volume prune`.

## Existing functionality

Local profile currently creates initial admin/manager/employee using the existing opt-in `CHAIRX_DEV_SEED_USERS=true` functionality. **Phase 1 implemented (pending CI):** with the demo overlay, the backend atomically creates three sample chair models with four variants, two suppliers and three customers. A demo-only marker ensures they are not duplicated on restart. Phase 2 adds three purchases through the real business services: one draft, one partially received, and one fully received across HOME and OFFICE warehouses. This code is pending CI verification. Phase 3 (pending CI) adds two stock-backed customer sales (self-pickup and city delivery) and a READY city delivery, using SaleService and DeliveryService. Physical returns, refunds, payments and closing remain **unimplemented** as demo fixtures. Do not call the complete demo fixture feature ready.

## Business fixture implementation contract

1. Phase 1 runs only with explicit `CHAIRX_DEMO_DATA_ENABLED=true`, `local` profile **and** a dedicated database name `chairx_demo`. Wrong database name fails closed before any writes. This flag is not enabled in the ordinary Compose stack.
2. Use a versioned dataset marker and deterministic external reference keys for each scenario. Seed transactionally using public application services; do not bypass validation, stock reservation, FIFO, financial posting, user rights, or daily closing. The operation should be idempotent on rebuild: re-running must neither duplicate nor silently overwrite user edits.
3. Fixture coverage should include several products/variants, suppliers, customers, warehouses, purchase drafts/confirmed/partially received/received, distinct receiving warehouses, sales drafts/confirmed/fulfilled/cancelled, deliveries in supported statuses, returns/refunds, defects, expenses, payments, bank/cash movements, staff/roles, and closing statuses **only when the actual domain permits each scenario**. Do not fabricate invalid state combinations.
4. Use obvious fictional labels such as `DEMO · ...`. Avoid real customer names, numbers, communications or credentials. Values are in KGS. Include meaningful dates and multi-line orders/receipts.
5. Ensure data is created **after Flyway and bootstrap**, handle concurrency/restarts, and ensure duplicate prevention under PostgreSQL. Keep production migrations fixture-free.
6. Add Testcontainers/PostgreSQL tests: empty demo DB seeding, second-run no duplicates, partial pre-existing data, modified records untouched, forbidden profile/database, simulated failure rollback, RBAC visibility, FIFO/financial balances. Run standard backend/frontend/CI.
7. Document `reset` as an explicit operation on the isolated demo volume. In-place selective deletion is unsafe for interconnected finance/stock records; rebuild the separate demo volume instead.

The demo backend should stay separate from any database containing valuable data.
