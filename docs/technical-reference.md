# ChairX — технический справочник по реализации и сопровождению

**Срез:** `main` `687c93605a5abf52be53ce15c5eda00a042268c7` (11.10.2026). Данный справочник описывает наблюдаемое устройство **исходников**. Считайте `SecurityConfig`, Flyway, приложения и тесты первичными источниками при расхождении с историческими отчётами F/P-пакетов.

## 1. Система в целом

```text
Browser / React 19 / TypeScript / Vite (http://localhost:5173)
              |
       same-origin /api proxy
              |
Spring Boot 4.1.1 / Java 25 / HTTP Basic + CSRF
 Controller/Web → DTO validation → Application Service
              |                |
          Domain rules      Audit/Finance/FIFO services
              |
    Spring JDBC / JdbcClient repositories
              |
     PostgreSQL 17 + Flyway V1…V52
```

**Стиль:** прагматичный **модульный монолит**, а не набор микросервисов; `backend/src/main/java/kg/chairx/{module}/{api,web,application,domain,persistence}` (в отдельных пакетах слой хранилища называется `infrastructure`). Данные хранятся в одной PostgreSQL. Транзакции оформляются через `@Transactional` и явные блокировки там, где участвуют деньги, FIFO, роли и финансовое закрытие. В `pom.xml` присутствует JPA starter, но прикладные репозитории изученных модулей опираются на Spring JDBC/JdbcClient — не заменяйте это предположениями о JPA-автогенерации.

### Модули backend

| Пакет / директория | Назначение, важные границы |
|---|---|
| `product` | Модели и вариации, цены, активность |
| `supplier` / `customer` | Поставщики и контакты клиентов |
| `warehouse` | Справочник активных складов |
| `purchase` | Закупки, подтверждение, частичная приёмка, карго, платежи поставщику |
| `inventory` | Остатки, резерв/блокировка, движения, перемещения; подкаталог `cost` — FIFO |
| `sale` | Черновики, подтверждение, резервы, выдача, отмена |
| `delivery` | Город/регион, планирование, отправка, вручение, неуспех, физическое возвращение |
| `payment` | Поступления оплаты продажи, каналы и отмены |
| `returning` / `refund` | Физический возврат и отдельный денежный возврат |
| `exchange` | Обмен и доплата/компенсация разницы |
| `defect` | Заблокированный брак, ремонт/запчасти/списание |
| `expense` | Операционные расходы |
| `finance` | CASH/BANK, ledger, CashFlow, расчёт и коррекции дневного отчёта |
| `dashboard` | Read-only показатели с фильтрацией по доступным authorities |
| `security` | Пользователи, роли, permission, bootstrap, SecurityAudit |
| `audit` / `common` / `config` | Доменный аудит, общие DB/HTTP-примитивы, конфигурация защиты и времени |

Frontend сгруппирован по `frontend/src/features/{admin,auth,catalog,customers,daily-closing,dashboard,deliveries,finance,inventory,purchases,returns,sales}`; маршруты — `frontend/src/app/router/AppRouter.tsx`, меню и права — `frontend/src/shared/lib/navigation.ts`, HTTP-клиент — `frontend/src/shared/api/`.

## 2. REST API: точки входа

Префикс **`/api`**. Полная **HTTP method + URL + permission** матрица, непосредственно извлечённая из `SecurityConfig`: [http-access-matrix.md](http-access-matrix.md). Ниже — навигация по группам, а **не** вся схема DTO:

| Группа | Ключевые запросы |
|---|---|
| Auth/CSRF | `GET /api/auth/me`, `GET /api/csrf` |
| Каталог | `/api/products`, `/api/product-variants` |
| Контрагенты | `/api/customers`, `/api/suppliers` |
| Склады | `/api/warehouses`, `/api/inventory/{overview,balances,movements,transfers,warehouse-summary}` |
| Закупки | `/api/purchases`, `/api/purchases/{id}/receipts`, `/payments`, `/cargo` |
| Продажи | `/api/sales`, `/api/sales/drafts`, `/{id}/confirm`, `/fulfill`, `/cancel` |
| Доставка | `/api/deliveries`, `/{id}/dispatch`, `/deliver`, `/fail`, `/return-to-warehouse` |
| Возвраты | `/api/returns`, `/api/refunds`, `/api/exchanges`, `/api/defects` |
| Финансы | `/api/payments`, `/api/expenses`, `/api/finance/{accounts,cash-flow,transfers,opening-balances,closings}` |
| Dashboard | `GET /api/dashboard/overview` (данные фильтруются по permissions) |
| Admin | `/api/admin/{users,roles,permissions,audit}` |

Некоторые исторические `docs/api.md` и `docs/security-rbac.md` описывают исходные контракты P20/P17 и **не перечисляют поздние F11/F12/UI-v2 endpoints**. Новые endpoints и фактическая защита определяются Java controller + `SecurityConfig`.

## 3. Security и RBAC

- **Аутентификация:** HTTP Basic, сотрудники из `app_users` и отдельный технический `catalog`. `GET /api/auth/me` выдаёт роль/эффективные права, не хеш. Секрет в React хранится **только в памяти вкладки**, не localStorage; сброс/выход очищают auth и приватный query-cache.
- **CSRF:** изменяющие операции требуют cookie + токен из `GET /api/csrf`. Даже ADMIN обязан предоставить CSRF; authorization и CSRF независимы.
- **RBAC:** сущности `security_roles`, `security_permissions`, `security_user_roles`, `security_role_permissions`. Суммирование прав множества ролей; при записи сервер не доверяет UI.
- **Админ:** все `/api/admin` требуют действующую реальную `system_role=true` / `code=ADMIN` **и** operation permission. Имеются защита последнего ADMIN, audit, `expectedVersion` + `reason` для обновления роли.
- **Защита неизвестного:** `SecurityConfig` завершает правила `anyRequest().denyAll()`, включая неожиданные HTTP-методы/маршруты. При расширении API нужно отдельно добавлять явное правило и отрицательные тесты.
- **Logout:** HTTP Basic не является серверной сессией и не поддерживает глобальное отключение ранее скопированных учётных данных без изменения/деактивации пользователя; это ограничение существующей архитектуры.

Полная seed-матрица 61 permission и реальные SQL-запросы для сверки: [roles-and-permissions.md](roles-and-permissions.md).

## 4. PostgreSQL, миграции, аудит

`backend/src/main/resources/db/migration/V1__…sql` по `V52__employee_lifecycle_permissions.sql` — **52 последовательные миграции**. Они создают и развивают бизнес-модули, финансовый журнал, security и версионируемое закрытие дня. При обновлении существующей базы **не редактируйте применённые миграции и не сбрасывайте таблицы**; добавляйте новую V53+ и тестируйте upgrade со старой схемы.

Ключевые области:
- Товар: `products`, `product_variants`; справочники `suppliers`, `customers`, `warehouses`.
- Закупка: `purchases`, `purchase_items`, `purchase_receipts`, `purchase_payments`.
- Склад/стоимость: `inventory_balances`, `stock_movements`, `inventory_cost_layers`, `inventory_cost_allocations`, `inventory_transfers`.
- Продажа/выдача: `sales`, `sale_items`, `deliveries`, `payments`, `returns`, `refunds`, `exchanges`, `defects`.
- Деньги: `finance_accounts`, `finance_movements`, `finance_daily_closings`, `finance_daily_closing_accounts`, версии, unlock/grant, изменения ожиданий и завершённые продажи.
- Доступ: `app_users`, `security_roles`, `security_permissions`, `security_user_roles`, `security_role_permissions`, `security_audit_log`; отдельно доменный журнал `audit_entries`.

**Заметка:** это перечень основных таблиц, не полная ER-диаграмма и не инструкция для ручного исправления данных. Денежные суммы CASH/BANK хранятся в сомах; в FIFO используются точные стоимостные вычисления. Физические остатки учитываются по паре склад/вариация.

## 5. Критические инварианты

1. **Склад:** `available=onHand-reserved-blocked`; отдельные балансы HOME/OFFICE; приёмка ограничена подтверждённой закупкой и неизрасходованным количеством. Чтение данных не создаёт складских движений.
2. **FIFO:** себестоимость слоёв распределяется из закупки с карго, резерв/списание/возврат не допускают повторного списания или восстановления. GET/POST операционного перемещения **не раскрывают** `totalCost`.
3. **Продажа:** `DRAFT` не резервирует и не платит; подтверждение резервирует атомарно, fulfillment списывает. Доставка и оплата — отдельные факты.
4. **Возврат:** обычный клиентский `returns` и возврат неудачной доставки на склад имеют разные допустимые состояния и операции.
5. **Деньги:** только контролируемые сервисы меняют счета; денежные события и баланс транзакционны. `FINANCE_TRANSFER` — перемещение, не доход. Нельзя молча «закрыть» расхождение изменением счёта.
6. **Идемпотентность:** ключи предусмотрены в закупочных выплатах, перемещениях, создании/коррекциях там, где их ждёт API. Не повторять POST вслепую после неизвестного сетевого результата; сверять документ и журнал.
7. **Закрытие:** дата и cutoff определяются в `Asia/Bishkek`; отчёт содержит наблюдаемый факт и пересчитанные ожидания. Исправление прошлой даты и поздние расходы имеют ограниченные окна и журнал аудита. Глобальная финансовая блокировка текущего закрытого дня остаётся в силе.

## 6. Development, тесты и CI

```bash
# В корне репозитория, только локально
bash scripts/init-local-env.sh
docker compose up -d --build --wait
docker compose ps

# Backend: из backend/
cd backend
./mvnw clean verify

# Frontend: из frontend/
cd ../frontend
npm ci
npm run lint
npm run typecheck
npm test
npm run build

# Выключить только контейнеры, сохранить БД
cd ..
docker compose down
```

- Java 25, PostgreSQL 17, Maven, Node 24. Docker Compose использует `postgres:17`, локальные порты **127.0.0.1:5432, :8080, :5173**, named volume `chairx_postgres_data`. Docker setup **специально для development**: local profile, local seeder, HTTP без публичного TLS.
- Backend tests используют PostgreSQL 17 Testcontainers с отдельно защищённой БД `chairx_test`; не подключайте тестовый runner к постоянным бизнес-данным.
- GitHub Actions: `.github/workflows/backend-verify.yml`, `frontend-verify.yml`, `local-dev-smoke.yml`, `f11-release-gate.yml` и доменные F08–F10 проверки. Для расширенного browser/PostgreSQL сценария используются `scripts/f11-browser-e2e.py`, `scripts/f11-operations-e2e.py`, `scripts/f13-admin-e2e.py`.
- При красном CI читать job logs и surefire report; не отключать падающие тесты ради зелёного статуса.
- Фикстуры для отдельной demo-БД на момент этого среза ещё были **в PR #47**, не в `main`. Не пытайтесь запускать их как уже выпущенную функциональность.

### Локальные секреты и DB persistence

`scripts/init-local-env.sh` один раз генерирует 4 случайных пароля для technical/admin/manager/employee, сохраняет их в `.env` без вывода в stdout. `DB_PASSWORD` берётся из `.env.example` и изначально **примерный**, его надо отдельно заменить при работе с небросовой БД. Замена ADMIN/manager/employee пароля в `.env` **не заменяет** хеши ранее созданных пользователей в PostgreSQL. То же касается первоначального `POSTGRES_PASSWORD` в уже инициализированном volume.

`docker compose down -v` удаляет постоянный volume PostgreSQL — **никогда не использовать с ценными данными**. Для резервной копии/проверочного восстановления см. `scripts/rehearse-db-upgrade.sh` и `docs/issue-32-acceptance.md`; с настоящими данными — только по разрешённой и безопасной процедуре.

## 7. Production gate (текущий статус: NO-GO)

Наличие полных модулей и успешного CI — **не разрешение для реальных пользователей в интернете**. Перед запуском необходимы: HTTPS, секреты и IAM-конфигурация для production, запрет dev-seeder, проверенные резервные копии и restore, миграция **реальной** исторической БД, физическая сверка CASH/BANK и склада, role-by-role UAT на телефоне/ПК и контроль утечек данных. Файл [F13](f13-production-readiness.md) фиксирует неполученные подтверждения.

**Особое ограничение:** `DailyClosingService` и `DailyClosingSalesSnapshot` реализуют **общую кассу по дате**, а не закрытие собственных продаж конкретного сотрудника. Это не баг в документации и не исправлено добавлением ролей — необходим отдельный бизнес/API дизайн, если предполагается персональная смена.

## 8. Правила поддержки документации и релизов

- Новый экран → проверить `AppRouter`, `navigation.ts`, operation permission и обновить [system-features.md](system-features.md).
- Новый endpoint → явное правило в `SecurityConfig`, 401/403/CSRF тест и обновить [http-access-matrix.md](http-access-matrix.md).
- Новая permission → отдельная Flyway V-мigration, role grants/negative tests, [roles-and-permissions.md](roles-and-permissions.md).
- Изменение бизнеса/FIFO/финансов → интеграционный тест real PostgreSQL и корректировка [business-rules.md](business-rules.md).
- Исторические документы P17/P20/F01 сохранены как отчёт конкретных стадий. Их инструкции про «будущий F02» или «неслитый P17» не обозначают реальный статус текущего `main`.

Ссылки на source-of-truth: [backend modules](../backend/src/main/java/kg/chairx/), [migrations](../backend/src/main/resources/db/migration/), [Frontend routes](../frontend/src/app/router/AppRouter.tsx), [SecurityConfig](../backend/src/main/java/kg/chairx/config/SecurityConfig.java), [CI](../.github/workflows/).
