# ChairX

**ChairX** — ERP/CRM для небольшого бизнеса по продаже кресел в Кыргызстане (8–20 продаж в день). Backend — модульный монолит на Java 25, Spring Boot, Maven, PostgreSQL 17 и Flyway. Работает с домашним и офисным складами; учёт ведётся в сомах.

Основные процессы: товары и вариации, поставщики и покупатели, закупки с карго и частичной приёмкой, **частичные выплаты поставщику и за карго**, складской учёт/FIFO, продажи из нескольких складов, самовывоз/городская/региональная доставка, возвраты и обмены, расходы, CASH/BANK, закрытие дня, брак и аудит. Backend предназначен для работы через REST API с будущим web/mobile frontend.

## Безопасность бизнес-API (пакет 17)

Пакет 17 добавляет разграничение доступа к операциям ERP по разрешениям сотрудников. Авторизация основана на существующих ролях и permissions в PostgreSQL; технический `catalog` имеет только чтение каталога. Неизвестные REST-маршруты запрещены по умолчанию. Сохраняются HTTP Basic и CSRF. Полная матрица HTTP-маршрутов, ролей, изменения Flyway V40 и регрессионные проверки — в [документации Security/RBAC](docs/security-rbac.md).

**Проверка перед merge:** `cd backend && mvn clean verify`. Пакет не должен объединяться до полного успешного прогона и ревью матрицы доступа.

## Локальный запуск

Нужны Java 25, Docker и Maven (или `backend/mvnw`).

```sh
docker compose up -d postgres
cd backend
./mvnw clean verify
```

Для запуска приложения необходимо задать значения из `.env.example`, в том числе `CHAIRX_CATALOG_PASSWORD`, затем выполнить `./mvnw spring-boot:run`. Файл `.env` не загружается Spring Boot автоматически. Интеграционные тесты используют отдельный PostgreSQL 17 Testcontainers (`chairx_test`), а не рабочую БД.

## Frontend — F01

Базовый адаптивный интерфейс ChairX находится в каталоге [`frontend/`](frontend/README.md): React, TypeScript, Vite и навигация по разделам. Для запуска: Node.js 24, `cd frontend && npm ci && npm run dev`. В F01 бизнес-экраны ещё являются заглушками; авторизация появится в F02.

## REST API

Аутентификация в текущей конфигурации — HTTP Basic. Изменяющие запросы требуют CSRF (получить токен через `GET /api/csrf`, сохранить cookie и передать заголовок). Бизнес-права сотрудников отдельно доводятся до готовности в **пакете 17**; **не публикуйте этот backend для сотрудников до проверки авторизации новых маршрутов.**

| Основные операции | Маршруты |
|---|---|
| Каталог и партнеры | `/api/products`, `/api/product-variants`, `/api/suppliers`, `/api/customers`, `/api/warehouses` |
| Закупка, приемка и оплаты | `/api/purchases`, `/api/purchases/{purchaseId}/receipts`, **`/api/purchases/{purchaseId}/payments`** |
| Остатки и движения | **`GET /api/inventory/balances`**, `GET /api/inventory/balances/{warehouseId}/{variantId}`, **`GET /api/inventory/movements`** |
| Продажи | `POST /api/sales`, **`GET /api/sales`**, `GET /api/sales/{id}`, `/fulfill`, `/cancel` |
| Доставка | **`GET /api/deliveries`**, `GET /api/deliveries/{id}`, `/dispatch`, `/deliver`, `/fail`, `/return-to-warehouse` |
| Возвраты/компенсации | **`GET /api/returns`**, `POST /api/returns`, `/api/refunds`, `/api/exchanges` |
| Брак | **`/api/defects`**: open, get, list, wait-for-parts, resolve, write-off |
| Деньги и закрытие | `/api/payments`, `/api/expenses`, `/api/finance/accounts`, `/api/finance/transfers`, `/api/finance/cash-flow`, `/api/finance/closings` |

Частичные закупочные выплаты имеют глобальный `idempotencyKey` (UUID), ограничены стоимостью товара/карго и создают по одной отрицательной проводке `PURCHASE_PAYMENT`. Получение товара не зависит от его полной оплаты, а выплаты не меняют FIFO.

Для оплаты продажи используются два финансовых метода — CASH и TRANSFER (= BANK). Параметр `channel` позволяет различать CASH, BANK_TRANSFER, MBANK и BANK_INSTALLMENT. Рассрочка отмечается `PAID` **только после фактического поступления денег в банк**. CashFlow теперь отдельно показывает `purchasePayments` в составе `totalOut`.

## Документация

- [Архитектура и границы модулей](docs/architecture.md)
- [Бизнес-правила и инварианты](docs/business-rules.md)
- [REST API: маршруты, DTO, пагинация и ошибки](docs/api.md)
- [Разработка, тестирование, миграции и интеграционные риски](docs/development.md)
- [Особенности пакета 19: CashFlow/сверка](docs/p19-financial-integrity.md)

## Состояние разработки

Пакет 20 реализуется в `feat/p20-backend-mvp` на основе `fix/p19-finance-integrity`; пакет 17 существует отдельно. До сообщения `BUILD SUCCESS` в локальном `mvn clean verify` и проверки объединённых веток нельзя считать пакет готовым к merge. Production readiness дополнительно требует deployment, backup/restore, permission matrix, secrets, monitoring и проверки реальных миграций БД. Frontend в этот пакет не входит.
