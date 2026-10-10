# ChairX F05 — Customer Management & Sales History

## Назначение и границы

F05 заменяет заглушку `/customers` рабочим интерфейсом CRM и добавляет
`/customers/:customerId`. Проект продолжает F01–F04: тот же AuthProvider,
ApiClient (HTTP Basic + CSRF), TanStack Query, AppLayout, PageHeader и Breadcrumbs.
Backend, миграции БД и модель безопасности не меняются.

F05 — **контактная карточка и просмотр существующих продаж**. В модуль не входят
создание продаж, переписка в WhatsApp/Instagram, автоматизация лидов,
LTV/прибыль клиента, объединение/удаление карточек, экспорт персональных данных.
Интеграция с отдельной карточкой продажи запланирована в F07.

## Маршруты и UI

- `/customers`: поиск, серверная пагинация (20 элементов), desktop-таблица
  и мобильные карточки, кнопка создания при `CUSTOMERS_CREATE`.
- `/customers/:customerId`: контакты (включая клиентов без имени), копирование
  телефона/WhatsApp, даты создания/изменения, статус, редактирование
  и деактивация/активация при `CUSTOMERS_MANAGE`, история заказов.
- Breadcrumbs: Главная → Клиенты → Карточка клиента.
- История показывает реальные поля `SaleSummary`: номер, дату, названия товаров,
  количество, сумму, статус продажи, оплаты и доставки.
  Пока нет реализованной страницы конкретной продажи — номера не являются ссылками.

## Backend API / permissions

| Метод и endpoint | Доступ | Назначение |
|---|---|---|
| GET /api/customers/overview?query&page&size | CUSTOMERS_READ + SALES_READ | список с orderCount, lastSaleNumber, lastOrderAt |
| GET /api/customers?page&size | CUSTOMERS_READ | список без статистики продаж |
| GET /api/customers/search?query | CUSTOMERS_READ | поиск до 20 совпадений |
| GET /api/customers/{id} | CUSTOMERS_READ | полная карточка |
| POST /api/customers | CUSTOMERS_CREATE | создание клиента |
| PUT /api/customers/{id} | CUSTOMERS_MANAGE | полная замена редактируемых полей |
| POST /api/customers/{id}/activate | CUSTOMERS_MANAGE | активация |
| POST /api/customers/{id}/deactivate | CUSTOMERS_MANAGE | деактивация (не удаление) |
| GET /api/sales/customer/{customerId}?page&size | CUSTOMERS_READ + SALES_READ | история продаж |

### Режимы списка

1. При наличии **обоих** `CUSTOMERS_READ` и `SALES_READ` используется
   `/api/customers/overview` с серверным фильтром `query`, `page=0`,
   `size=20`. Для пагинации используется backend `total`.
2. При `CUSTOMERS_READ` без `SALES_READ` используется
   `/api/customers` (серверная пагинация с `totalElements`).
   При поиске применяется отдельный backend `/api/customers/search`, который
   отдаёт **максимум 20** совпадений. UI явно предупреждает об ограничении
   и не показывает выдуманный total или страницы. Статистика продаж скрыта.
3. При отсутствии `CUSTOMERS_READ` маршруты недоступны; API не вызывается.
   `CUSTOMERS_CREATE` само по себе не даёт права чтения клиентов.

Поиск обновляется с debounce 350 мс, страница сбрасывается на 0 при изменении
ввода. Query keys содержат режим списка, поисковую строку, страницу и размер;
результаты разных фильтров не смешиваются.

### Создание и обновление

Поля: имя, основной/дополнительный телефон, WhatsApp, Instagram, город/регион,
адрес, комментарий. Имя необязательно, однако хотя бы один из четырёх контактных
каналов должен быть заполнен.

Лимиты из backend: имя 200, номера 50, Instagram 100, город/регион 200,
адрес 500, комментарий 2000. Номера — **строки**, сохраняются плюс и начальные
нули. Пустые необязательные поля передаются как `null` (без подставных имён).

PUT передаёт **все** редактируемые поля, а не patch одного поля.
Форма блокирует повторное сохранение во время pending, сохраняет введённый
текст при ошибке; успех показывается только по ответу сервера. После POST
происходит переход в карточку и обновление списков. После PUT/смены активности
инвалидируются списки и обновляется detail query.

### Проверка возможных дублей

При создании из заполненного контакта (от 4 символов) выполняется
`GET /api/customers/search?query=...` после задержки 400 мс.
Показываются первые пять найденных карточек и ссылки для просмотра.
Совпадение **не гарантирует** дублирование: нет auto-merge и запрета POST.
Ошибка lookup не блокирует форму. Если POST завершился сетевой ошибкой,
сотрудник получает предупреждение, что карточка могла сохраниться, и
должен проверить контакт до повтора.

### Security и lifecycle

Доступ контролируется permissions из F02/P23, **не именами ролей**
ADMIN/MANAGER/EMPLOYEE. UI не делает запрещённых запросов. Backend остаётся
источником истины в проверке доступа, CSRF и валидации.
401 инициирует очистку авторизации существующим ApiClient/AuthProvider;
403 показывается как отказ в доступе; 404 — карточка не найдена.
Другие ошибки отображаются без backend stack trace; пользователь может
повторить чтение.

Клиентские данные не записываются в localStorage/sessionStorage,
не отправляются третьим сторонам, не логируются. TanStack Query держит
карточки только в памяти; существующий AuthProvider очищает cache при logout.
Деактивация не равнозначна удалению персональных данных и не удаляет продажи.

## Архитектура

- `features/customers/api.ts`: typed endpoints и query keys;
- `types.ts`: backend DTO;
- `validation.ts`: нормализация и валидация;
- `components/CustomerForm.tsx`: поля, сохранение, duplicate warning;
- `components/CustomerSearch.tsx`: поле поиска;
- `components/CustomerTable.tsx`: desktop/mobile список;
- `components/CustomerPager.tsx`: пагинация;
- `components/CustomerOrderHistory.tsx`: запросы с SALES_READ;
- `pages/CustomersPage.tsx` и `pages/CustomerDetailPage.tsx`: экраны.

## Тесты и проверка

Тесты: `frontend/src/test/customers.test.tsx`, плюс существующая регрессия
F01–F04. Проверяют оба режима списка, query/debounce, server pages,
создание без имени и с одним контактом, duplicate warning, отсутствие
лишних запросов и повторного POST, ошибки/валидацию, полную передачу PUT,
активацию и деактивацию, историю продаж, custom roles и deny по permissions.

Команды из `frontend/`:

```bash
npm ci
npm run lint
npm run typecheck
npm run test
npm run build
```

GitHub Actions: `Frontend verification` и `Local development stack smoke`.
Итоговые статусы CI следует сверить в PR; успешный build не заменяет smoke.

## Ограничения

- Для пользователя без `SALES_READ` search backend ограничен 20 результатами,
  поэтому невозможно показать полный count или серверную пагинацию поиска.
- Duplicate lookup не гарантирует уникальность; повтор POST после timeout
  требует ручной сверки.
- История заказов пока read-only; переход в продажу — F07.
- Issue #20 про раскрытие себестоимости складских перемещений не относится
  к F05 и исправляется отдельным backend PR до production.
