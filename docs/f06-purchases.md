# ChairX F06 — Purchases, Suppliers & Receiving

## Назначение

Frontend-пакет F06 использует существующие backend API. Процесс:
поставщик → черновик закупки (`DRAFT`) → позиции (реальные вариации) →
закупочные цены и карго → подтверждение (`CONFIRMED`) →
частичное поступление (`PARTIALLY_RECEIVED`) → полное поступление (`RECEIVED`).
Разрешена отмена (`CANCELLED`) только в допускаемых backend состояниях.

Поступление запускается **только через backend** `POST /api/purchases/{id}/receipts`,
который атомарно создаёт складское движение и FIFO-партию.
Frontend не меняет остатки/FIFO и не ведёт отдельный ledger.
Оплаты закупок в F06 только читаются; `POST /api/purchases/{id}/payments` — отдельный **F06.1**.

## Маршруты

| Route | Реальная функция | Разрешение |
|---|---|---|
| `/purchases` | закупки, фильтры, операции | PURCHASE_READ |
| `/purchases/:purchaseId` | карточка, статус, карго | PURCHASE_READ |
| `/purchases/receipts` | worklist и приёмка | INVENTORY_RECEIVE |
| `/purchases/suppliers` | поставщики | SUPPLIERS_READ |
| `/purchases/payments` | выплаты конкретной закупки по UUID | PURCHASE_PAYMENTS_READ |

Подразделы используют существующий Sidebar и Breadcrumbs; вложенные маршруты
не становятся отдельными основными разделами. Путь `/purchases` перенаправляет
сотрудника без PURCHASE_READ к его разрешённому подразделу (например, приёмке).
Custom role получает доступ по **effective permissions**, а не имени роли.

## Контракты, API и DTO

- `GET /api/purchases/overview?status&supplierId&from&to&number&page&size` —
  PurchaseOverviewPage: items/page/size/total. Список отображает реальную
  поставку, статус, даты, itemCount, ordered/received/remaining, goodsCost,
  cargoCost и totalCost. Все цены видны только при PURCHASE_READ.
  `number` фильтрует UUID, человекочитаемого номера API не возвращает.
  `from`/`to` — серверный диапазон дат в Asia/Bishkek.
- `GET /api/purchases/{id}` — PurchaseResponse с позициями и их UUID.
- `POST /api/purchases` и `PUT /api/purchases/{id}` —
  `{supplierId,items:[{productVariantId,orderedQuantity,purchaseUnitCost}],cargoCost,comment}`.
  PUT заменяет всё содержимое только для DRAFT.
- `POST /api/purchases/{id}/confirm` / `cancel` —
  явное подтверждение действия; backend окончательно проверяет статус, выплаты,
  количество уже полученного товара.
- `PUT /api/purchases/{id}/cargo` — `{cargoCost: "1500.00"}`.
  Изменение доступно лишь DRAFT/CONFIRMED и когда `costsLockedAt=null`.
  После приёмки карго заблокировано, нельзя указать сумму ниже выплат.
- `GET /api/purchases/receiving?page&size` —
  **безопасные позиции worklist**, не полные закупки, без себестоимости.
- `GET /api/purchases/{id}/receiving-summary` —
  **безопасные позиции одной закупки**, также без себестоимости.
- `POST /api/purchases/{id}/receipts` —
  `{idempotencyKey,warehouseId,items:[{purchaseItemId,quantity}],comment}`.
  Важно: `purchaseItemId` — ID **позиции закупки**, не вариации.
- `GET /api/suppliers?page&size` и `GET /api/suppliers/{id}`;
  `POST /api/suppliers`, `PUT /api/suppliers/{id}`,
  `POST /api/suppliers/{id}/activate|deactivate`.
- `GET /api/purchases/{purchaseId}/payments` —
  PurchasePaymentSummaryResponse с обязательствами, выплатами и остатками;
  кнопки создания платежа нет, общего endpoint для всех выплат нет.

## Выбор вариации, закупочные цены и карго

Используется **готовый** `features/catalog/api.ts` с серверными страницами
`GET /api/products?page` и `GET /api/products/{id}/variants?page`.
Выбор — пошаговый, по активной модели/вариации. Не выдумывается поиск
по всей базе, SKU или UUID. В уже созданной закупке, если название не
загружено, показывается реальный UUID вместо выдуманного названия.
Читаемые названия не подтягиваются N+1 запросами по всем позициям.

Создание/редактирование из UI требует PURCHASE_CREATE/PURCHASE_UPDATE
**плюс SUPPLIERS_READ и CATALOG_READ** для выбора поставщика и вариаций.
Это дополнительные *требования UI*, не новая проверка backend и не
автоматическая выдача прав. Поставщики подгружаются со **всех страниц**
с ограничением на число страниц; неактивные исключаются из выбора для новых
закупок. Позиции: минимум одна, максимум 1000, вариация не повторяется,
количество — целое положительное, стоимость и карго — неотрицательные
десятичные строки (макс. 17 целых и 2 дробных знака), комментарий
макс. 4000 символов. Внутренний FIFO не рассчитывается в UI.

Пустое карго — `null`, а не 0. До первой приёмки backend требует
**явно определённое** карго. При отсутствии расходов пользователь вводит `0`.
После PUT/POST инвалидация затрагивает нужные записи/листы/query keys;
нет optimistic финансовых операций.

## Частичная приёмка

Пример: заказаны 10 чёрных и 5 серых кресел. Первая приёмка
6 чёрных + 2 серых на выбранный активный склад; вторая —
4 чёрных + 3 серых (остаток обновляет backend).
Форма показывает заказано/принято/осталось, принимает только целые
положительные количества не больше remainingQuantity; пустые/нулевые
позиции не включаются в `items`. Требуется активный склад.

Для выбора склада используется `GET /api/warehouses` существующего
`inventoryApi.warehouses`; ему требуется **INVENTORY_READ**.
Поэтому практическая роль кладовщика обычно включает
`INVENTORY_RECEIVE + INVENTORY_READ`; **PURCHASE_READ не требуется**.
Если INVENTORY_READ отсутствует, UI честно блокирует выбор склада,
не запрашивая запрещённый API.

### Идемпотентность и неизвестный результат

- Для *каждой новой* операции создаётся один UUID через `crypto.randomUUID()`.
- Ключ, warehouseId, items и comment составляют один неизменный payload.
- Одновременный повтор блокирует `useRef` + disabled UI.
- Если возник сетевой сбой после POST, frontend **не считает приёмку
  неуспешной**: блокирует редактирование/новую операцию и предлагает
  повторить **тот же запрос с тем же UUID**.
- В случае определённой серверной ошибки (400/403/409) пользователь видит
  ошибку и может исправить данные с новой попыткой.
- После подтверждённого успеха инвалидируются receiving worklist/summary,
  overview/detail закупки и inventory overview/balances/movements.
- **Ограничение:** неизвестная операция хранится только в памяти React.
  После полной перезагрузки страницы этот payload теряется. У кладовщика
  с INVENTORY_RECEIVE нет `GET /api/purchases/{id}/receipts`
  (требуется PURCHASE_READ). Поэтому после перезагрузки *нельзя*
  гарантировать восстановление/проверку результата без проверки администратором.
  Не выполняйте новую приёмку до сверки. Устойчивое восстановление потребует
  отдельного серверного API или согласованного механизма и отдельного пакета.

## Критическая backend-уязвимость: P1

**Внимание: frontend F06 НЕ устраняет эту уязвимость.**
В актуальном backend `SecurityConfig` допускает
`INVENTORY_RECEIVE` на `POST /api/purchases/{id}/receipts`.
`PurchaseController.receive` возвращает `PurchaseReceiptResponse`,
где вложенный `PurchaseReceiptItemResponse` содержит
**`allocatedCargoCost` и `totalCost`**.
Пользователь складской роли без PURCHASE_READ может получить себестоимость,
читая **сырой ответ HTTP**, даже если React не показывает эти поля.

Отдельное backend исправление отслеживается в
[Issue #24](https://github.com/Medetovichv/chairX/issues/24).
Нужно выдать warehouse-safe DTO ответа без cost и сохранить
внутренние FIFO/транзакционность/идемпотентность, затем проверить raw JSON
под ролью только INVENTORY_RECEIVE. F06 преднамеренно принимает
`POST` response как `unknown`, не выводит поля, но **до backend-fix**
производственная приёмка ограниченной складской ролью небезопасна.

## Поставщики и оплаты

SUPPLIERS_READ разрешает список/просмотр, SUPPLIERS_MANAGE —
создать/редактировать/активировать/деактивировать. DELETE нет;
неактивный поставщик сохраняется в истории.

`/purchases/payments` принимает UUID, показывает только соответствующую
PurchasePaymentSummaryResponse при PURCHASE_PAYMENTS_READ:
выплаты поставщику/карго, CASH/BANK, даты, reference и остатки;
сводки считаются **backend**, не frontend. При отсутствии PURCHASE_READ
платёжный экран не вызывает закрытый список закупок для выбора ID.
POST оплаты и изменения ledger не входят в F06.

## Архитектура

- `features/purchases/api.ts`: реальный API, отдельные query keys.
- `types.ts`: backend DTO, `validation.ts`: форматирование, проверка.
- `pages/PurchasesPage`, `PurchaseDetailPage`, `ReceivingPage`,
  `SuppliersPage`, `PurchasePaymentsPage`.
- `components/PurchaseForm`, `ProductVariantPicker`, `ReceivingForm`,
  `SupplierForm`, `PurchasePager`, `PurchaseStatusBadge`, `PurchaseTabs`.
- Только существующие ApiClient, AuthProvider, QueryClient, дизайн F01–F05.

## Проверки

```bash
cd frontend
npm ci
npm run lint
npm run typecheck
npm run test
npm run build
```

Дополнительно GitHub Actions: `Frontend verification` и
`Local development stack smoke` (PostgreSQL + backend + frontend,
ephemeral test credentials). Фактические результаты проверок
необходимо сверить в PR #23. Старые тесты F01–F05 сохраняются.

### Известные ограничения

- Пагинация каталога без серверного поиска: выбор модели/вариации постраничный.
- При множестве поставщиков all-picker запрашивает страницы последовательно.
- Кандидаты для закупки требуют SUPPLIERS_READ и CATALOG_READ дополнительно.
- Названия вариаций в карточке без отдельного разрешённого API не
  гидратируются массовыми запросами; отображаются реальные UUID.
- После refresh неопределённая приёмка требует ручной сверки.
- Серверная утечка cost (Issue #24) требует отдельного исправления.
- История выплат read-only; создание выплат — F06.1.
