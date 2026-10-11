# ChairX — точная HTTP-матрица контроля доступа

**Автоматическая сверка по исходному `backend/src/main/java/kg/chairx/config/SecurityConfig.java`** в `main` после слияния [PR #51](https://github.com/Medetovichv/chairX/pull/51), commit `5aded0960f68de41f12eae1f614d55678881646b` (11.10.2026). Это **81 явная группа Spring Security HTTP matchers / 133 пары method+path-pattern**, а не подсчёт уникальных Java controller methods или гарантия, что каждый pattern имеет отдельный handler. Маршруты сгруппированы точно по применяемым правилам.

## Правила чтения

- `*` обозначает **один сегмент пути** (например, `/api/sales/*`), а не свободную замену любой глубины. Не добавляйте скрытые или незащищённые дочерние endpoints.
- Для всех известных URL действует **HTTP method + route**; `GET` не даёт право `POST`. Последнее правило — **`anyRequest().denyAll()`**. Order of matchers matters.
- `A или B` — достаточно любого из прав. `A и B` — требуется **оба**. **active system ADMIN + X** — проверяется реальная запись системной роли ADMIN и отдельно разрешение X, не только буквенное имя или интерфейс.
- Для безопасного чтения всё равно нужна аутентификация; для `POST/PUT/PATCH/DELETE` требуется CSRF. Из таблицы не следуют права обходить ограничения состояния или финансовых дат.
- Это **authorization layer**; запрос должен иметь существующий backend controller, корректные DTO, valid business state и другие проверки. Список пользовательских страниц находится в [system-features.md](system-features.md).

## Перечень правил по источнику (source order)

| Метод | URL pattern(s) | Достаточные permission/условие |
|---|---|---|
| `GET` | `/api/csrf`<br>`/api/auth/me` | Authenticated |
| `GET` | `/api/products`<br>`/api/products/*`<br>`/api/product-variants/*`<br>`/api/products/*/variants` | CATALOG_READ **или** CATALOG_ACCESS |
| `POST` | `/api/products`<br>`/api/products/*/variants`<br>`/api/products/*/activate`<br>`/api/products/*/deactivate`<br>`/api/product-variants/*/activate`<br>`/api/product-variants/*/deactivate` | CATALOG_MANAGE |
| `PUT` | `/api/products/*`<br>`/api/product-variants/*` | CATALOG_MANAGE |
| `GET` | `/api/suppliers`<br>`/api/suppliers/*` | SUPPLIERS_READ |
| `POST` | `/api/suppliers`<br>`/api/suppliers/*/activate`<br>`/api/suppliers/*/deactivate` | SUPPLIERS_MANAGE |
| `PUT` | `/api/suppliers/*` | SUPPLIERS_MANAGE |
| `GET` | `/api/customers/overview` | CUSTOMERS_READ **и** SALES_READ |
| `GET` | `/api/customers`<br>`/api/customers/search`<br>`/api/customers/*` | CUSTOMERS_READ |
| `POST` | `/api/customers` | CUSTOMERS_CREATE |
| `POST` | `/api/customers/*/activate`<br>`/api/customers/*/deactivate` | CUSTOMERS_MANAGE |
| `PUT` | `/api/customers/*` | CUSTOMERS_MANAGE |
| `GET` | `/api/warehouses`<br>`/api/warehouses/*` | INVENTORY_READ |
| `POST` | `/api/warehouses`<br>`/api/warehouses/*/activate`<br>`/api/warehouses/*/deactivate` | WAREHOUSES_MANAGE |
| `PUT` | `/api/warehouses/*` | WAREHOUSES_MANAGE |
| `GET` | `/api/purchases/*/payments` | PURCHASE_PAYMENTS_READ |
| `POST` | `/api/purchases/*/payments` | PURCHASE_PAYMENTS_CREATE |
| `POST` | `/api/purchases/*/receipts` | INVENTORY_RECEIVE |
| `GET` | `/api/purchases/receiving`<br>`/api/purchases/*/receiving-summary` | INVENTORY_RECEIVE |
| `GET` | `/api/purchases/overview` | PURCHASE_READ |
| `GET` | `/api/purchases`<br>`/api/purchases/*`<br>`/api/purchases/*/receipts`<br>`/api/purchases/*/receipts/*` | PURCHASE_READ |
| `POST` | `/api/purchases` | PURCHASE_CREATE |
| `PUT` | `/api/purchases/*`<br>`/api/purchases/*/cargo` | PURCHASE_UPDATE |
| `POST` | `/api/purchases/*/confirm` | PURCHASE_CONFIRM |
| `POST` | `/api/purchases/*/cancel` | PURCHASE_CANCEL |
| `GET` | `/api/inventory/overview`<br>`/api/inventory/warehouse-summary` | INVENTORY_READ |
| `GET` | `/api/inventory/balances`<br>`/api/inventory/balances/*/*`<br>`/api/inventory/movements`<br>`/api/inventory/transfers`<br>`/api/inventory/transfers/*` | INVENTORY_READ |
| `POST` | `/api/inventory/transfers` | INVENTORY_TRANSFER |
| `GET` | `/api/dashboard/overview` | SALES_READ **или** DELIVERIES_READ **или** INVENTORY_READ |
| `GET` | `/api/sales`<br>`/api/sales/*` | SALES_READ |
| `GET` | `/api/sales/customer/*` | SALES_READ **и** CUSTOMERS_READ |
| `POST` | `/api/sales` | SALES_CREATE |
| `POST` | `/api/sales/drafts` | SALES_CREATE |
| `PUT` | `/api/sales/*/draft` | SALES_DRAFT_MANAGE |
| `POST` | `/api/sales/*/confirm` | SALES_DRAFT_MANAGE |
| `POST` | `/api/sales/*/fulfill` | SALES_UPDATE |
| `POST` | `/api/sales/*/cancel` | SALES_CANCEL |
| `GET` | `/api/deliveries`<br>`/api/deliveries/*` | DELIVERIES_READ |
| `POST` | `/api/deliveries`<br>`/api/deliveries/*/dispatch`<br>`/api/deliveries/*/deliver`<br>`/api/deliveries/*/fail`<br>`/api/deliveries/*/return-to-warehouse` | DELIVERIES_MANAGE |
| `POST` | `/api/deliveries/*/cancel` | SALES_CANCEL |
| `PUT` | `/api/deliveries/*/planned-date` | DELIVERIES_MANAGE |
| `GET` | `/api/returns`<br>`/api/returns/*` | RETURNS_READ |
| `POST` | `/api/returns` | RETURNS_CREATE |
| `GET` | `/api/payments/*`<br>`/api/payments/sale/*`<br>`/api/payments/sale/*/active` | PAYMENTS_READ |
| `POST` | `/api/payments` | PAYMENTS_CREATE |
| `POST` | `/api/payments/*/cancel` | PAYMENTS_CANCEL |
| `GET` | `/api/refunds/*`<br>`/api/refunds/sale/*` | REFUNDS_READ |
| `POST` | `/api/refunds` | REFUNDS_CREATE |
| `GET` | `/api/exchanges/*`<br>`/api/exchanges/*/settlements` | EXCHANGES_READ |
| `POST` | `/api/exchanges` | EXCHANGES_CREATE |
| `POST` | `/api/exchanges/*/settlements` | EXCHANGES_SETTLE |
| `GET` | `/api/expenses`<br>`/api/expenses/*`<br>`/api/expenses/total` | EXPENSES_READ |
| `POST` | `/api/expenses` | EXPENSES_CREATE |
| `GET` | `/api/defects`<br>`/api/defects/*` | DEFECTS_READ |
| `POST` | `/api/defects`<br>`/api/defects/*/wait-for-parts`<br>`/api/defects/*/resolve` | DEFECTS_MANAGE |
| `POST` | `/api/defects/*/write-off` | DEFECTS_WRITE_OFF |
| `GET` | `/api/finance/my-closings`<br>`/api/finance/my-closings/*` | DAILY_CLOSING_READ |
| `POST` | `/api/finance/my-closings/*` | DAILY_CLOSING_WRITE |
| `GET` | `/api/finance/closings/*/history` | FINANCE_READ **и** DAILY_CLOSING_AUDIT_READ |
| `GET` | `/api/finance/closings/*/sales` | FINANCE_READ |
| `GET` | `/api/finance/closings/*/access`<br>`/api/finance/closings/*/preview` | FINANCE_READ |
| `GET` | `/api/finance/closings`<br>`/api/finance/closings/*` | FINANCE_READ |
| `POST` | `/api/finance/closings/*/unlock` | `FINANCE_READ` **и** (`DAILY_CLOSING_UNLOCK_MANAGER` **или** `DAILY_CLOSING_UNLOCK_ADMIN`) |
| `POST` | `/api/finance/closings/*/lock` | FINANCE_READ **и** DAILY_CLOSING_UNLOCK_ADMIN |
| `POST` | `/api/finance/closings/*/expenses` | FINANCE_READ **и** DAILY_CLOSING_EXPENSE_CORRECT |
| `PUT` | `/api/finance/closings/*` | FINANCE_READ **и** DAILY_CLOSING_WRITE |
| `POST` | `/api/finance/closings/*` | `FINANCE_READ` **и** (`DAILY_CLOSING_WRITE` **или** `FINANCE_CLOSE`) |
| `GET` | `/api/finance/accounts`<br>`/api/finance/cash-flow` | FINANCE_READ |
| `POST` | `/api/finance/transfers` | FINANCE_TRANSFER |
| `POST` | `/api/finance/opening-balances` | FINANCE_INITIALIZE |
| `GET` | `/api/admin/users`<br>`/api/admin/users/*` | **active system ADMIN** + USERS_READ |
| `POST` | `/api/admin/users` | **active system ADMIN** + USERS_CREATE |
| `PATCH` | `/api/admin/users/*/deactivate` | **active system ADMIN** + USERS_DEACTIVATE |
| `PATCH` | `/api/admin/users/*/activate` | **active system ADMIN** + USERS_ACTIVATE |
| `POST` | `/api/admin/users/*/reset-password` | **active system ADMIN** + USERS_RESET_PASSWORD |
| `GET` | `/api/admin/roles` | **active system ADMIN** + ROLES_READ |
| `POST` | `/api/admin/users/*/roles` | **active system ADMIN** + ROLES_ASSIGN |
| `DELETE` | `/api/admin/users/*/roles/*` | **active system ADMIN** + ROLES_ASSIGN |
| `GET` | `/api/admin/roles/*`<br>`/api/admin/permissions`<br>`/api/admin/audit` | **active system ADMIN** + ROLES_READ |
| `POST` | `/api/admin/roles` | **active system ADMIN** + ROLES_CREATE |
| `PUT` | `/api/admin/roles/*` | **active system ADMIN** + ROLES_UPDATE |

**По умолчанию:** `anyRequest().denyAll()` блокирует неизвестные маршруты, методы и неописанные операции; не менять на глобальный `authenticated()` ради прохождения тестов.

## Проверки на уровне данных

- `GET /api/dashboard/overview` допускается при одном из `SALES_READ`, `DELIVERIES_READ`, `INVENTORY_READ`, но сервис *отдельно* скрывает метрики, на которые у пользователя нет полномочий (null/пустой список).
- `GET /api/customers/overview` и `GET /api/sales/customer/*` требуют **одновременно** `CUSTOMERS_READ` и `SALES_READ`.
- `GET/POST /api/inventory/transfers` не раскрывают FIFO `totalCost` в ответных операционных DTO. Permission `INVENTORY_READ` не открывает себестоимость.
- `GET /api/finance/closings/*`, `/preview`, `/sales` продолжают работать с **общим** днём CASH/BANK и требуют `FINANCE_READ`. Только `/api/finance/my-closings/*` представляет личную смену без доступа к суммам других работников; см. [протокол](employee-daily-closing.md).
- Операции `/api/admin/**` требуют системную роль **ADMIN**, даже если у custom role каким-то образом окажется аналогичный permission.
- Финансовые изменения проходят доменные сервисы и транзакции; `FINANCE_CLOSE` или `DAILY_CLOSING_WRITE` не обходят время/перепроверку под блокировкой.

## Порядок изменения HTTP API

1. Добавить Java endpoint + request/response DTO и validation.
2. Добавить строго определённое `HttpMethod` + path rule до `anyRequest().denyAll()`.
3. Выбрать существующее разрешение или создать Flyway-migration с грантами.
4. Написать реальные негативные интеграционные тесты: 401, 403, CSRF, отсутствие побочных эффектов.
5. Сверить RBAC для `ADMIN/MANAGER/EMPLOYEE` и кастомных ролей; обновить [матрицу ролей](roles-and-permissions.md), [маршруты UI](system-features.md) и этот файл.
6. Подтвердить `mvn clean verify` в CI с PostgreSQL 17 Testcontainers.

Источник — [SecurityConfig.java](../backend/src/main/java/kg/chairx/config/SecurityConfig.java). Таблица создавалась из actual matcher source в PR #51; при будущих изменениях Java необходимо обновлять её, а не считать автоматически синхронизированной.
