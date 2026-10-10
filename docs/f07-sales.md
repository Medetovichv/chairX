# ChairX F07 — Sales Management & Order Workflow

## Архитектура и источники истины

Frontend F07 использует существующий Spring SaleController и DTO из
`backend/src/main/java/kg/chairx/sale/`. Никаких backend/Flyway изменений
в PR #27 нет. Фичи организованы в `frontend/src/features/sales`:
`api.ts`, `types.ts`, `validation.ts`, `pending-draft.ts`,
`pages/{SalesPage,CreateSalePage,SaleDetailPage}.tsx` и
`components/{SalesTable,SalesKanban,SaleForm,SaleItemsEditor,SaleVariantPicker,CustomerPicker,SaleStatusBadge}.tsx`.
Переиспользуются F05 customers, F03 catalog, F04 inventory, общий ApiClient,
AuthProvider, QueryClient, UI-компоненты и существующая навигация.

Продажи, остатки, резерв, складские движения, цены и workflow принадлежат
backend/PostgreSQL. React не списывает, не резервирует и не подсчитывает
фактические финансовые движения.

## API / DTO

| Операция | Endpoint | Permission |
|---|---|---|
| Список | `GET /api/sales?page=0&size=20&status&from&to&number` | SALES_READ |
| Деталь | `GET /api/sales/{id}` | SALES_READ |
| Создание DRAFT | `POST /api/sales/drafts` | SALES_CREATE |
| Полная замена DRAFT | `PUT /api/sales/{id}/draft` | SALES_DRAFT_MANAGE |
| Подтверждение DRAFT | `POST /api/sales/{id}/confirm` | SALES_DRAFT_MANAGE |
| Выполнение | `POST /api/sales/{id}/fulfill` | SALES_UPDATE |
| Отмена | `POST /api/sales/{id}/cancel` | SALES_CANCEL |

Типы следуют фактическим backend record:
`SalePageResponse{items,page,size,total}`; `SaleSummary` содержит
`id,saleNumber,createdAt,customerId,customerName,total,status,fulfillmentType,
quantity,products,paymentStatus,deliveryStatus,plannedDeliveryDate`.
Список не вызывает `PAYMENTS_READ` и не загружает финансовые API.
Он никогда не показывает себестоимость и прибыль.

`SaleResponse` содержит `id,saleNumber,customerId,fulfillmentType,status,items,
total,createdBy,createdAt,fulfilledBy,fulfilledAt,cancelledBy,cancelledAt,comment`.
`SaleItemResponse`: `id,productVariantId,warehouseId,quantity,unitSalePrice,total`.
**В SaleResponse нет paymentStatus/deliveryStatus/plannedDeliveryDate.**
Карточка получает соответствующий `SaleSummary` отдельным серверным
`GET /api/sales?number=<saleNumber>&page=0&size=20` под SALES_READ,
ищет совпадающий UUID и показывает только фактические значения.
При недоступном ответе может использовать соответствующий SaleSummary
из навигации; если данных нет — сообщает, что статус недоступен.
Это один запрос на карточку, а не N+1 запросы списка.

## Продажи: таблица и Kanban

`/sales` получает реальные продажи, серверную пагинацию (20),
фильтры SaleStatus `DRAFT, CONFIRMED, FULFILLED, CANCELLED`,
диапазон дат Asia/Bishkek и поиск по номеру заказа.
Поиск debounce 350 мс. Не используются N+1 запросы для списка.

Kanban имеет четыре колонки, каждая вызывает собственный
`GET /api/sales?status=...&page=...&size=20` через
`useInfiniteQuery`. Кнопка «Показать ещё» грузит следующую страницу.
Заголовок колонки показывает реально загруженное количество и total.
Завершённые и отменённые заказы также видны по соответствующим статусам.
Глобальный фильтр статуса действует только в таблице, даты/номер —
на обеих вкладках. Drag&Drop не внедрён: все переходы backend-действия
с подтверждением в карточке. Keyboard/mobile работают через ссылки и кнопки.

## Создание черновика

Маршрут `/sales/new` доступен с SALES_CREATE независимо от SALES_READ
(сотрудник только с SALES_CREATE перенаправляется сюда с `/sales`).
`POST /api/sales/drafts` принимает
`{idempotencyKey,customerId,fulfillmentType,items,comment}`.
`customerId`, `fulfillmentType` могут быть null, items — пустой массив.
Эти данные допустимы для DRAFT **по backend**, но не для подтверждения.
`comment` ограничен 2000 символами.

Каждая товарная позиция:
`{productVariantId,warehouseId,quantity,unitSalePrice}`.
`quantity` — положительное целое; `unitSalePrice` — **целые сомы,
от 0, до 19 цифр**, без копеек (`@Digits(integer=19,fraction=0)`).
Backend не принимает customer-name/variant-name/price suggestions в запросе.
Повторяющийся UUID вариации с одного склада в UI не разрешается.

Клиент выбирается через F05 `GET /api/customers/search?query` только
при CUSTOMERS_READ. Поиск ограничен backend максимум 20 совпадениями;
его нельзя принимать за полный каталог. При наличии CUSTOMERS_CREATE
пользователь может открыть `/customers` и использовать готовую F05-форму.
Без CUSTOMERS_READ можно сохранить черновик без клиента.
Неактивные клиенты не выбираются; backend проверяет и при сохранении.

Каталог выбирается через F03 page API (20 моделей и 20 вариаций).
Рекомендованная цена предлагается только по явному нажатию;
никогда не подставляется автоматически. Для новых строк нужны
CATALOG_READ и INVENTORY_READ (для выбора склада и просмотра доступности).
Вариация и склад хранят **реальные UUID**.
Просмотр баланса: `GET /api/inventory/balances/{warehouseId}/{variantId}`
только с INVENTORY_READ. Браузер не резервирует товар.
Для незавершённого редактирования без этих прав уже существующие UUID
передаются в PUT без потери неизменённых полей.

Черновик создаётся с UUID идемпотентности, записанным в
`sessionStorage` *до* отправки POST, привязанным к сотруднику и вкладке.
После сетевой ошибки, HTTP 5xx или 409 форма блокирует создание
нового запроса и предлагает повтор исходного JSON с **тем же ключом**,
включая восстановление после reload в этой вкладке.
При недоступном/повреждённом storage POST запрещён.
Закрытие вкладки или очистка хранилища не гарантирует восстановления;
перед новым действием нужна сверка. Секреты и пароли в payload не хранятся.

## Черновик, подтверждение, склад

Карточка `/sales/:saleId` загружает свежий `SaleResponse` при SALES_READ.
Редактирование через полный `PUT /draft` доступно только DRAFT и
SALES_DRAFT_MANAGE. Сохраняются все строки/цены/склады/клиент/способ
получения/комментарий, не только изменённое поле.
Возможный сетевой тайм-аут не приравнивается к отказу: пользователь
должен загрузить актуальный черновик перед новым изменением.

Для `POST /confirm` нужны SALES_DRAFT_MANAGE, непустые позиции и
способ получения. Диалог показывает заказ, клиента, сумму и тип
получения. Backend повторно валидирует клиента, товары, склады и
**доступный остаток**, после чего резервирует inventory.
Не следует считать CONFIRMED финальной продажей: товар ещё не выдан.

`POST /fulfill` допускается с SALES_UPDATE только для
`CONFIRMED + SELF_PICKUP`. Только backend освобождает резерв и
выполняет SALE_OUT/FIFO. Для доставок вызов запрещён: операции
`CITY_DELIVERY/REGION_DELIVERY` относятся к **F08**.

`POST /cancel` доступен с SALES_CANCEL только для DRAFT или
CONFIRMED + SELF_PICKUP. Backend проверяет активные оплаты,
обмены/возвраты и освобождает резерв, когда это разрешено.
Завершённые продажи не отменяются через этот endpoint.

POST изменения статуса не запускается автоматически: нужен диалог.
Перед каждым необратимым действием карточка делает свежий `GET /api/sales/{id}`
и сравнивает текущий статус, клиента, способ получения, позиции,
количество, целочисленную цену и сумму с тем, что сотрудник видел
в диалоге. При изменениях операция не отправляется, карточка получает
новые данные и требует заново просмотреть заказ. Если денежное значение
в JS не представимо точно, подтверждение блокируется без округления.
Это защита от устаревшего экрана, **не замена version check в backend**:
между preflight GET и POST всё ещё возможна гонка; для строгого
конкурентного редактирования потребуется отдельный backend-контракт.

При потере ответа на PUT черновика UI сохраняет заполненную форму
заблокированной. После GET серверных данных сравнивается именно
отправленный набор полей: если запись найдена, пользователь увидит
подтверждение; иначе локальные правки сохраняются до явного отказа
от них. Слепой повтор PUT не выполняется.

После подтверждённого ответа адресно обновляются
`sales list, kanban, sale detail, inventory overview/balance/movements`.
При неопределённой ошибке UI не показывает успех и блокирует
повторение до проверки актуального состояния на backend.

## Permission matrix

| Действие | Требуется |
|---|---|
| Список и карточка | SALES_READ |
| Создание DRAFT | SALES_CREATE |
| Редактирование, подтверждение | SALES_DRAFT_MANAGE + SALES_READ (для UI карточки) |
| Выполнение SELF_PICKUP | SALES_UPDATE + SALES_READ (для UI карточки) |
| Отмена разрешённых продаж | SALES_CANCEL + SALES_READ (для UI карточки) |
| Поиск/выбор клиента | CUSTOMERS_READ |
| Создать клиента через F05 | CUSTOMERS_CREATE + CUSTOMERS_READ (для UI страницы) |
| Выбрать новую вариацию | CATALOG_READ |
| Выбор склада/информационные остатки | INVENTORY_READ |

Используются effective permissions, а не названия системных ролей.
При отсутствии права UI не отправляет запросы к защищённым endpoint,
но backend окончательно проверяет доступ и бизнес-условия.

## UX и ограничения

- Mobile: адаптивные карточки в таблице и доступный по кнопкам Kanban,
  удобные формы с 44px+ кликабельными областями, без сложных DnD.
- Ошибки: 401 через текущий AuthProvider, 403 ForbiddenPage,
  404 NotFoundPage, 409/validation/network через существующий ApiClientError.
- В SaleResponse названия вариаций не включены; при разрешённом
  CATALOG_READ карточка может подгрузить реальные названия по UUID.
  Список остаётся без N+1 запросов.
- Доставка, платежи клиентов, возвраты, обмены, повреждения, финансовое
  закрытие и WhatsApp/Instagram не входят в F07.
- Настоящий интеграционный сценарий подтверждения продаж требует
  PostgreSQL/warehouse stock. Docker smoke проверяет общий стек;
  отдельный E2E со складским списанием должен быть отмечен отдельно.

## Проверки

```bash
cd frontend
npm ci
npm run lint
npm run typecheck
npm run test
npm run build
```

GitHub Actions: Frontend verification + Local development stack smoke.
Фактические run IDs и результаты см. PR #27.
