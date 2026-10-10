# ChairX F02.1 — Navigation & UX Structure

## Delivery boundaries
This package extends F01/F02 on the actual merged P22/P23 backend. It changes
**frontend only** (plus this document). No backend routes, Flyway migrations or
permissions were created. HTTP Basic, CSRF, F02 AuthProvider and API-client
semantics stay unchanged.

The work is intentionally UI scaffolding: no fabricated orders, customers,
warehouse balances, financial amounts or statistics. The visual filters are
local view modes, not live backend filters. Text search, creation buttons,
data tables and daily-closing actions do not call business APIs in F02.1.

## Primary navigation (11 sections)
| Group | Primary section | URL |
|---|---|---|
| Обзор | Главная | \`/\` |
| Ежедневная работа | Продажи | \`/sales\` |
| Ежедневная работа | Доставки | \`/deliveries\` |
| Ежедневная работа | Склады | \`/inventory\` |
| Ежедневная работа | Клиенты | \`/customers\` |
| Ежедневная работа | Закрытие дня | \`/daily-closing\` |
| Управление | Каталог товаров | \`/catalog\` |
| Управление | Закупки | \`/purchases\` |
| Управление | Возвраты и брак | \`/returns\` |
| Управление | Финансы | \`/finance\` |
| Система | Администрирование | \`/admin\` |

Поставщики, Расходы, Обмены and Брак are now only child entries.
The day closing module stays separate from broad finance access.

## Route permissions
The backend permission is a **read permission** unless otherwise noted.
Actual backend route security, not the React application, makes final access
decisions. In particular, admin APIs still demand active system ADMIN in the
database in addition to effective USERS_/ROLES_ authority.

| Frontend path | Effective permission(s) | Notes |
|---|---|---|
| \`/\` | authenticated | F02 login required |
| \`/sales\` | SALES_READ | create button visible only with SALES_CREATE, disabled |
| \`/deliveries\` | DELIVERIES_READ | |
| \`/inventory\` | INVENTORY_READ | "Дом"/"Офис" labels only; UUIDs not hardcoded |
| \`/customers\` | CUSTOMERS_READ | nullable names, never invented |
| \`/daily-closing\` | DAILY_CLOSING_READ **or** FINANCE_READ | does not require full finance for staff |
| \`/catalog\` | CATALOG_READ | creation button conditional on CATALOG_MANAGE |
| \`/purchases\` | PURCHASE_READ for main purchases page | if absent, first permitted subsection |
| \`/purchases/receipts\` | INVENTORY_RECEIVE | independent of PURCHASE_READ and cost data |
| \`/purchases/payments\` | PURCHASE_PAYMENTS_READ | |
| \`/purchases/suppliers\` | SUPPLIERS_READ | |
| \`/returns\` | RETURNS_READ for main returns page | otherwise first accessible child |
| \`/returns/exchanges\` | EXCHANGES_READ | |
| \`/returns/defects\` | DEFECTS_READ | |
| \`/finance\` | FINANCE_READ for overview | otherwise first accessible child |
| \`/finance/expenses\` | EXPENSES_READ | no FINANCE_READ required |
| \`/finance/cash-flow\` | FINANCE_READ | |
| \`/admin\` | USERS_READ or ROLES_READ | always redirects to first permitted child |
| \`/admin/users\` | USERS_READ | |
| \`/admin/roles\` | ROLES_READ | |

All primary sections are displayed when at least one relevant read permission
is held; unrelated child tabs are always hidden. A direct attempt to access a
forbidden child route results in ForbiddenPage, not a client-side permission
escalation. These frontend checks do not replace backend access control.

## Compatibility redirects
All redirects are **same-origin React Router** redirects and preserve the
target route guard:

- \`/suppliers\` → \`/purchases/suppliers\`
- \`/expenses\` → \`/finance/expenses\`
- \`/exchanges\` → \`/returns/exchanges\`
- \`/defects\` → \`/returns/defects\`

## Component structure
- \`shared/lib/navigation.ts\`: primary groups, child route metadata,
  permissions, readable route labels, allowed default child selection,
  compatibility aliases and breadcrumbs data.
- \`app/router/AppRouter.tsx\`: authenticated route guards and same-origin
  compatibility redirects.
- \`layouts/Sidebar.tsx\`, \`MobileNavigation.tsx\`, \`Breadcrumbs.tsx\`:
  independent child navigation, active states, mobile drawer, breadcrumbs.
- \`pages/OperationalPage.tsx\`: screen-specific column headings and empty
  worklists for sales, deliveries, inventory, customers, catalog, purchasing,
  returns, finance and administration.
- \`pages/DailyClosingPage.tsx\`: report field layout without financial data.
- Shared presentation: \`PageHeader\`, \`PageTabs\`, \`PageToolbar\`,
  \`StatusBadge\`, and existing \`EmptyState\`, \`LoadingState\`, \`ErrorState\`.

No Redux/Zustand, new UI framework or universal table abstraction.

## Quality gates
Run from \`frontend/\`:

\`\`\`sh
npm ci
npm run lint
npm run typecheck
npm run test
npm run build
\`\`\`

The GitHub Actions Frontend verification and Local development stack smoke
runs must both finish green at the exact final PR head before merging. Backend
and migrations were not modified by this package.
