# ChairX

**ChairX** — ERP/CRM для небольшого бизнеса по продаже кресел в Кыргызстане (8–20 продаж в день). Backend — модульный монолит на Java 25, Spring Boot, Maven, PostgreSQL 17 и Flyway. Работает с домашним и офисным складами; учёт ведётся в сомах.

Основные процессы: товары и вариации, поставщики и покупатели, закупки с карго и частичной приёмкой, **частичные выплаты поставщику и за карго**, складской учёт/FIFO, продажи из нескольких складов, самовывоз/городская/региональная доставка, возвраты и обмены, расходы, CASH/BANK, закрытие дня, брак и аудит. Backend предназначен для работы через REST API с будущим web/mobile frontend.

## Безопасность бизнес-API (пакет 17)

Пакет 17 добавляет разграничение доступа к операциям ERP по разрешениям сотрудников. Авторизация основана на существующих ролях и permissions в PostgreSQL; технический `catalog` имеет только чтение каталога. Неизвестные REST-маршруты запрещены по умолчанию. Сохраняются HTTP Basic и CSRF. Полная матрица HTTP-маршрутов, ролей, изменения Flyway V40 и регрессионные проверки — в [документации Security/RBAC](docs/security-rbac.md).

**Проверка перед merge:** `cd backend && mvn clean verify`. Пакет не должен объединяться до полного успешного прогона и ревью матрицы доступа.


## Локальный запуск — F01.1

Docker Compose запускает PostgreSQL 17, Spring Boot Java 25 и Vite Node.js 24 одной командой.
Первоначально нужен Docker Desktop и Compose v2.22+.

~~~sh
bash scripts/init-local-env.sh
~~~

Скрипт один раз создаёт локальный .env с четырьмя случайными паролями (при существующем файле ничего не меняет). Затем:

~~~sh
docker compose up -d --build --wait
~~~

Откройте http://localhost:5173. Для обычных запусков используйте
docker compose up -d; для HMR/frontend и автоматической пересборки backend —
docker compose up --watch. Остановка: docker compose down (не добавляйте -v).

При первой инициализации автоматически создаются admin/ADMIN, manager/MANAGER
и employee/EMPLOYEE. Пароли сохраняются в БД только в виде хешей и не меняются
при повторных запусках. Режим доступен только для локальной разработки;
вход через UI появится в F02.

Подробная инструкция: [Локальная разработка](docs/local-development.md).

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

Backend P15–P21 уже объединён в main. F01 находится в PR #12; F01.1 построен поверх F01 и ожидает отдельного ревью. Прохождение CI не означает готовность production deployment.
