# Аудит кода и документации ChairX — 11 октября 2026

**Тип:** статический анализ репозитория `Medetovichv/chairX` в `main`, commit `687c93605a5abf52be53ce15c5eda00a042268c7`. Изучены дерево исходников, Spring Security, Flyway, React Router/навигация, бизнес-сервисы и существующие документы F/P. В рамках этого документационного PR **не изменялись Java/TypeScript/SQL/тесты**, **не выполнялся локальный runtime или production smoke**. Не смешивать факт существования кода с доказанным пользовательским UAT.

## Подтверждённая структура

| Факт | Источник |
|---|---|
| Java 25 / Spring Boot 4.1.1; PostgreSQL 17; React 19 / Vite / TS | `backend/pom.xml`, `frontend/package.json`, `docker-compose.yml` |
| Модули товара, клиентов, закупок, склада/FIFO, продаж, доставок, возвратов/обменов/брака, финансов и безопасности | `backend/src/main/java/kg/chairx` |
| Flyway V1–V52 с системными ADMIN/MANAGER/EMPLOYEE | `backend/src/main/resources/db/migration` |
| 61 permission в базовом миграционном каталоге; ADMIN=61, MANAGER=38, EMPLOYEE=11 | V29, V30, V33, V40, V42, V46, V51, V52; [seed matrix](../roles-and-permissions.md) |
| Дополнительные пользовательские роли; назначение нескольких ролей; versioned update; last-ADMIN guard | `RoleManagementService`, `UserManagementService`, `RoleAssignmentService`, `SecurityConfig` |
| UI маршруты рабочих разделов F03–F12 и обновления UI-v2 до PR #46 | `AppRouter.tsx`, `navigation.ts`, соответствующие `frontend/src/features/*` |
| Доступ к dashboard учитывает разрешения, не отдаёт метрики без соответствующего права | `DashboardService` и `DashboardOverview` |
| Старая проблема #38 с `totalCost` в переносах **исправлена** | `InventoryTransferDetailsResponse`, `InventoryTransferResponse`, `InventoryTransferQueryService`, `InventoryTransferController`; см. PR #20 |
| Закрытие дня опирается на **общие** счета CASH/BANK и aggregate completed sales | `DailyClosingController`, `DailyClosingService`, `DailyClosingSalesSnapshot`, `DailyClosingPage` |

## Выявленные важные расхождения

**P1 — бизнес-требование «личное закрытие кассы сотрудником» не реализовано.** В актуальном сервисе `finance_daily_closings` уникален по business date и хранит общие ожидаемые/фактические остатки `CASH` и `BANK`. `GET /api/finance/closings/{date}/preview` и `/{date}` доступны с `DAILY_CLOSING_READ` **или** `FINANCE_READ`. EMPLOYEE имеет `DAILY_CLOSING_READ` и `DAILY_CLOSING_WRITE` по V42. `DailyClosingSalesSnapshot.live` фильтрует все завершения за дату, **не по actor/seller**. Следствие: эта реализация **не гарантирует** требуемую изоляцию личных денег/продаж, и сотрудник с текущими seed-grants может увидеть общие closing CASH/BANK. Это факт архитектуры и прав, а не утверждение, что система уже открыта для реальных работников. Нужны отдельное решение бизнес-владельца и разработка позднее, если требование остаётся обязательным; **не исправлять это скрытием колонок только на фронтенде**.

**P1 — production rollout всё ещё NO-GO** согласно `docs/f13-production-readiness.md`: требуется отдельное одобрение, HTTPS/управление секретами, tested real-data restore/upgrade, сверка остатков и физические/UAT проверки. Локальный Compose имеет `SPRING_PROFILES_ACTIVE=local` и `CHAIRX_DEV_SEED_USERS=true`, не заменяет production окружение.

**P2 — основные README устарели.** Предыдущий корневой README утверждал, что F02 / frontend ещё впереди, описывал F01.1 и P17 как незавершённые, несмотря на F12/UI-v2 в main. Предыдущий `frontend/README.md` также называл F03 единственным готовым экраном и упоминал ранее устранённую утечку себестоимости. Эти README заменены актуальными ссылками/описаниями.

**P2 — старые отчёты содержат «текущие» статусы старых веток.** `docs/architecture.md`, `docs/api.md`, `docs/development.md`, `docs/local-development.md` и отдельные F/P документы содержат исторические инструкции про несведённые ветки, pending F02 или P17. Они сохраняются для трассируемости разработки, а **обзор действующей системы** теперь дан в `README.md`, [technical-reference](../technical-reference.md), [roles](../roles-and-permissions.md) и [features](../system-features.md).

**Проверено, но не считать открытым багом:** ранее опубликованная issue #38 описывала `totalCost` для складского сотрудника. В текущем `main` GET list/detail и POST DTO для перемещений не содержат cost. Фактический runtime JSON, финансовые расчёты и история приложения требуют стандартных регрессионных тестов, но прежняя проблема не должна называться «неисправленной» только из-за старого Issue.

**Отдельная незавершённая работа:** `feat/dev-demo-data-fixtures` / PR #47 на момент аудита **не слит в main**; не включайте demo-seed как готовую пользовательскую возможность.

## Что изменено в рамках docs-PR

- Полностью обновлён `README.md` как главный вход в репозиторий.
- `docs/system-features.md`: карты всех экранов, функций, прав и бизнес-процессов.
- `docs/roles-and-permissions.md`: 61 permission, реальные grants ADMIN/MANAGER/EMPLOYEE, custom roles и SQL-аудит.
- `docs/http-access-matrix.md`: карта method+URL для `SecurityConfig`.
- `docs/technical-reference.md`: архитектура, схема модулей, безопасность, БД, тестирование, deployment.
- `frontend/README.md`: актуальные UI/тесты/ссылки.

## Перед принятием этого PR

1. Сверить ссылки Markdown и отсутствие противоречия исходникам текущего `main`.
2. Проверить, что не затронуты runtime-файлы, Flyway, секреты и тесты.
3. Не приписывать CI, браузерное UAT или production-ready статус **этому** docs-PR без фактического нового запуска.
4. Если `main` изменится, обновить зафиксированный baseline SHA и повторить проверку матриц.

**Результат:** существующая система описана как реализована; ранее согласованные, но отсутствующие возможности названы прямо. Документация сама по себе ничего не «чинит» в финансовых и role-based сценариях.

## Follow-up: отдельное исправление персонального закрытия (PR #51)

Исходное замечание P1 было корректным для описанного выше commit `687c936`. Следующая ветка `fix/employee-personal-daily-closing` вводит защищённый `/api/finance/my-closings/**`, V53 с персональными snapshots и **FINANCE_READ** как обязательный дополнительный барьер всех корпоративных endpoints `/api/finance/closings/**`. Корпоративный F11/P21 остаётся неизменным как финансовый ledger/report, личный snapshot не меняет деньги. Персональный отчёт содержит только созданные сотрудником завершённые продажи и зарегистрированные им платежи. Это не означает полной изоляции `/api/sales` по продавцу, не заменяет восстановление/приёмку/F13 GO-LIVE и не устраняет человеческие ошибки сверки. Ссылки: [личная касса](../employee-daily-closing.md), [PR #51](https://github.com/Medetovichv/chairX/pull/51).
