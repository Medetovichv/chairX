# ChairX F04 — Inventory & Warehouse Operations

## Scope / architecture
Frontend feature only, based on merged F03 and F02.1. The router maps
\`/inventory\` to \`features/inventory/pages/InventoryPage\`; navigation,
auth, HTTP client, CSRF headers, QueryClient, loading/error/empty components
are reused. No backend endpoints or database schema changed.

The view uses three independent domains of server data:
- Paginated **WarehouseResponse** master list (GET /api/warehouses);
- **InventoryOverviewPage** read model for full/selected warehouse;
- paginated movement and transfer histories.
No per-variant calls are issued when rendering the global stock table.

### Real endpoints
| Request | Purpose / required permission |
|---|---|
| GET /api/warehouses?page=N&size=100 | All warehouse names, codes, IDs, activity; INVENTORY_READ |
| GET /api/inventory/overview?warehouseId=&model=&variation=&includeZero=&onlyAvailable=&page=&size= | All stock or one warehouse; INVENTORY_READ |
| GET /api/inventory/balances/{warehouseId}/{variantId} | Precise single balance; INVENTORY_READ |
| GET /api/inventory/movements?warehouseId=&variantId=&page=&size= | Movement history, both IDs mandatory; INVENTORY_READ |
| GET /api/inventory/transfers?warehouseId=&variantId=&page=&size= | Transfer history; INVENTORY_READ |
| GET /api/inventory/transfers/{id} | Confirm uncertain transfer result; INVENTORY_READ |
| POST /api/inventory/transfers | Idempotent transfer; INVENTORY_TRANSFER |

The warehouse dropdown uses actual \`id\` values from the server and fetches
all pages sequentially (100 per page, maximum 1,000 pages as an explicit
safety guard). Inactive warehouses remain selectable for historical viewing;
they are **excluded from transfer source/destination choices**. Additional
warehouses dynamically produce table columns. The UI never assumes warehouse
list order nor invents a HOME/OFFICE UUID.

## On-hand, reserved, blocked, available
- \`onHand\`: physical units on a particular warehouse or all warehouses.
- \`reserved\`: units already committed to other workflows.
- \`blocked\`: non-available units, e.g. defective/quarantine inventory.
- \`available\`: units the backend says may be used for new transactions.

All four fields are displayed **as returned**, not derived from events,
summed in the UI or optimistically changed. When a variant has no row for
a given warehouse, that single warehouse displays **0**. In selected-warehouse
view, the backend supplies the selected warehouse's totals. The frontend
never reuses all-warehouse totals as one warehouse's stock.

The overview contract contains \`items\`, \`page\`, \`size\`, \`total\`
(**no totalPages**). Page counts are calculated from \`total / size\`.
Server-side \`model\` and \`variation\` search inputs have a 350-ms debounce.
Availability filter uses either \`onlyAvailable=true\`,
\`includeZero=true\`, or neither; all changes reset the page to zero.
The query key includes all relevant parameters, so an old warehouse cannot
be confused with a newly selected warehouse.

The stock position panel reuses the already loaded \`productVariantId\` and
warehouse rows; individual balance and movement requests begin **only**
after an explicit warehouse is chosen. No movement creation/stock adjustments
are available. Physical stock can be changed only by existing audited
purchase, sale, return and transfer backend workflows.

## Transfer workflow and idempotence

The user selects an overview row, two **active, distinct** warehouses and an
integer positive quantity not exceeding the known \`available\` for the
source. The UI displays a final confirmation (model, variant, warehouses,
quantity) before POST. The backend validates again under transactional locks.

On confirmation, \`crypto.randomUUID()\` is called **once** to create
\`transferId\`, freezing source, destination, variant and quantity. While a
request is being sent the UI blocks another click; no optimistic balance
updates occur. The same payload/ID must be sent again on retry. On
successful POST the UI invalidates inventory overview, balance, movements
and transfer-list TanStack Query prefixes, then displays success.

If the browser receives a network error, an invalid response or server 5xx,
the transfer outcome is treated as **unknown**, not failed. The form locks
editing and dismissal and offers:
1. GET /api/inventory/transfers/{transferId}, verifying original fields;
2. resend the *unchanged* payload with the *same* transferId.

A 404 during checking does not generate a new ID; the user may retry the
same POST. If the application/browser is forcibly closed or reloaded while
unknown, the in-memory ID is lost. This is a remaining limitation of F04:
the user must resolve such an event with an administrator and audit history
before starting a replacement transfer. No credentials, sensitive stock
operations or auth tokens are persisted to localStorage/sessionStorage.
The existing F02 authentication session is in-memory only.

## Permissions

| Profile permissions | Read overview/history | Initiate transfer | Read catalog card |
|---|---|---|---|
| INVENTORY_READ | Yes | No | Only if CATALOG_READ |
| INVENTORY_READ + INVENTORY_TRANSFER | Yes | Yes | Only if CATALOG_READ |
| INVENTORY_TRANSFER without INVENTORY_READ | No | No (UI needs inventory read model) | Independent |
| No INVENTORY_READ | No | No | Independent |

The UI tests effective permissions from \`AuthProvider\`, not role names,
including custom P23 roles. Backend SecurityConfig is authoritative. An
\`INVENTORY_READ\` employee without \`CATALOG_READ\` still sees model/
variation names in InventoryOverviewPage and can work without querying catalog.

## CRITICAL BACKEND DATA-EXPOSURE FINDING (not fixed by F04)

**Verified against main SecurityConfig and DTO on 2026-10-10.**
SecurityConfig allows:
\`GET /api/inventory/transfers\` and \`GET /api/inventory/transfers/*\`
with \`INVENTORY_READ\` alone.
However, \`InventoryTransferDetailsResponse\` has \`totalCost: BigDecimal\`,
and \`InventoryTransferPageResponse\` embeds that DTO; GET /transfers
and GET /transfers/{id} compute/return \`totalCost\` from cost allocations.
The POST \`InventoryTransferResponse\` also includes \`totalCost\`.
Thus a stock reader **can see internal cost through the HTTP response** even
though F04 intentionally never displays it in tables, badges or forms.
Concealing a column in React **does not eliminate this security issue**.
Do not treat it as resolved by this PR.

**Required minimal follow-up backend/security package before production rollout:**
create a cost-free transfer DTO for inventory-reader APIs (list/detail);
keep cost fields only in independently authorized finance/cost-specific
routes (or restrict existing cost routes to an explicit financial permission
and provide a separate cost-free inventory-reader endpoint). Review the
POST transfer response as well: it must not reveal cost to a non-financial
\`INVENTORY_TRANSFER\` user. Add security integration tests that read the raw
JSON as \`INVENTORY_READ\` and \`INVENTORY_TRANSFER\` roles and assert that
\`totalCost\` and other cost fields are entirely absent, while any authorized
finance endpoint retains its intended behavior. No backend code is modified
in F04.

## Handling errors, tests, limitations
The existing ApiClient handles 401 (F02 logout), 403 (permissions), 404
(missing item), 409 (conflict, refresh stock), validation 400, offline/5xx
and JSON parse errors without displaying backend stack traces. The
read model does not provide product names in the general transfer list;
F04 only displays confirmed name from a selected stock row or a shortened
variant UUID, not N+1 product lookups.

Mobile uses stacked stock cards; desktop uses a scrollable table with one
column per actual warehouse. Histories and forms are responsive.

Run quality gates:
\`\`\`bash
cd frontend
npm ci
npm run lint
npm run typecheck
npm run test
npm run build
\`\`\`
GitHub Actions also runs a full Docker Compose smoke test with PostgreSQL.
Test data is mocked only within Vitest, never within production UI.
