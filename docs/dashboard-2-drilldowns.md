# Dashboard 2.0 — KPI drilldowns

## Result
All five dashboard KPI cards and both attention links now open real, server-filtered lists.
Sales operational metric cards also open their corresponding status filters. URL state replaces
session-only filter persistence on Sales, Deliveries and Inventory, so direct links and browser
Back/Forward restore mode, dates, status, search, warehouse, availability, view and pagination.
Unrelated parameters, including Sales/Deliveries quick-view state, are retained by filter edits.

| Dashboard entry | List query and meaning |
| --- | --- |
| Orders today | Sales `operational=false&from=<businessDate>&to=<businessDate>`; creation day in Asia/Bishkek |
| In work | Sales `operational=false&activeOnly=true`; exactly DRAFT and CONFIRMED, all dates |
| Pending deliveries | Deliveries `operational=false&pendingOnly=true`; exactly READY and IN_TRANSIT |
| Delivered today | Deliveries `operational=false&deliveredOn=<businessDate>`; DELIVERED and completion timestamp in that Bishkek day |
| Delayed deliveries | Deliveries `operational=false&pendingOnly=true&plannedTo=<businessDate minus one day>`; null planned dates excluded |
| Available stock | Inventory `availability=recorded`; rows with stock/reserve/blockage, quantities reconcile to all balances, not a row count |
| Low stock | Inventory `lowStock=true`; aggregate available quantity per variant between 1 and 5 inclusive, across all warehouses by default |

The server's dashboard business date anchors time-sensitive links even when browser time differs.
Dashboard numbers and subsequent lists are separate requests, so concurrent operational changes can
change totals between them. There is no snapshot guarantee.

## Read API additions
- `/api/sales`: optional `activeOnly` boolean.
- `/api/deliveries`: optional `pendingOnly` boolean and `deliveredOn` ISO date.
- `/api/inventory/overview`: optional `lowStock` boolean; when a warehouse is selected, aggregate within that warehouse.

All filters apply before pagination and to both result rows and total count. Existing query callers
retain previous defaults through overloads. Existing RBAC gates and DTO fields remain in place.
No migrations, dependencies, financial calculations, posting flows, state transitions or dashboard
metric definitions changed. Kanban cache keys include `activeOnly` to avoid reusing unrestricted results.

Malformed URL enums, booleans, pages, dates and warehouse IDs use safe defaults; reversed valid date
ranges show an error and suppress the listing request. Unknown but well-formed warehouse IDs show
an explicit recovery action. Empty warehouse selection restores correctly during Back/Forward.

## Applied skill
Read Vercel React Best Practices and `rerender-derived-state-no-effect` from PR #129 at
`9f39160be0b030e69c4339cb6f46949d6fcb4935`. List state is derived directly from the URL instead of
synchronizing URL and persisted local copies through effects. No skill files were imported into this
branch and neither PR #129 nor PR #130 was merged.

## Validation
- Frontend: `npm ci --no-audit --no-fund`, lint, typecheck, **476 tests in 30 files**, production build passed.
- Added 12 frontend regression cases: seven dashboard-to-list transitions, direct URL restoration,
  filter history on all three pages, invalid/reversed ranges, pagination and unrelated parameter preservation.
- Backend: `./mvnw --batch-mode --no-transfer-progress clean verify` passed: **730 tests**, zero failures/errors,
  one existing skip (`operatorProvidedBackup`, requires an operator-supplied backup).
- Added four real PostgreSQL integration tests: metric/list agreement, inclusive/exclusive Bishkek
  midnight boundaries, completion versus creation date, exact status sets, pagination beyond 20 rows,
  aggregate low-stock boundaries and cross-warehouse split stock, query validation and authorization.
- Headless Brave browser with **fixture API responses**: 14 KPI/history scenarios at 1440×1000 and 390×844,
  no page JavaScript errors or document horizontal overflow. Eight screenshots inspected, including
  all three destination pages at both widths. Backend semantics verified separately against PostgreSQL;
  these browser runs are not an end-to-end test against a live backend.
- Existing production bundle size warning (>500 kB) remains. No merge or deployment performed.
