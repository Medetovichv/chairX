# ADR 0002: внутренний Inventory

Inventory — складской механизм. Purchase, Sale, Return, StockTransfer, StockReservation и Defect в этом этапе отсутствуют. Пользовательских команд для изменения остатков через HTTP нет.

## Граница модуля

`kg.chairx.inventory.application.InventoryService` предоставляет:

- `recordMovement(RecordStockMovement)` — одно физическое изменение остатка;
- `getBalance(warehouseId, variantId)` — неизменяемый snapshot; для существующих справочников без остатка возвращает нули без INSERT;
- `listMovements(warehouseId, variantId, page, size)` — журнал одной пары с пагинацией (до 100 записей).

`api` содержит входную команду и страницу журнала; `domain` — неизменяемые records InventoryBalance/StockMovement, enum и бизнес-ошибки; `persistence` — один конкретный JDBC repository. Это не JPA entities и не универсальный CRUD-framework. Product и Warehouse используются только через их существующие application services. Обратных зависимостей нет.

Складские количества — целые long/BIGINT. `available()` вычисляется как onHand − reserved − blocked; отдельной колонки available нет. Доступность не означает стоимость или финансовый результат.

## Изменение склада

Все восемь типов поддержаны: PURCHASE_IN, SALE_OUT, RETURN_IN, TRANSFER_OUT, TRANSFER_IN, WRITE_OFF, ADJUSTMENT_IN, ADJUSTMENT_OUT. quantity всегда положительное; направление задаёт enum, а не знак числа. Приход увеличивает onHand, расход уменьшает только доступное количество. reserved и blocked эта операция не меняет.

Сервис проверяет существование склада и варианта. Деактивация справочника сама по себе не запрещает отражать физические факты (например, списание старого товара). Правила допуска конкретной покупки/продажи и permissions принадлежат соответствующему вызывающему бизнес-модулю, который ещё не реализован.

Будущий SALE_OUT по резерву должен сопровождаться контролируемым погашением резерва в той же общей транзакции; списание заблокированного брака аналогично потребует соответствующей операции Defect. Сейчас такие специализированные операции отсутствуют: они не эмулируются прямым изменением counters и не расходуют чужие reserved/blocked.

## Транзакции и конкуренция

Явный JDBC выбран для INSERT ON CONFLICT и SELECT FOR UPDATE, без зависимости от первого уровня кеша JPA. Используются существующие JdbcClient и Spring transaction manager; JDBC-аудит и складские изменения участвуют в общей транзакции с JPA бизнес-модулей. Это проверено rollback-тестом.

Для новой транзакции — READ COMMITTED. Операция присоединяется к существующей транзакции вызывающего модуля (REQUIRED), не использует REQUIRES_NEW. Внешние сценарии должны использовать совместимую изоляцию READ COMMITTED.

1. Проверка, не выполнен ли operationId ранее.
2. Проверка Warehouse/ProductVariant через публичные сервисы.
3. INSERT пустого остатка ON CONFLICT DO NOTHING: PK защищает параллельное первое создание.
4. SELECT FOR UPDATE конкретной пары Warehouse + ProductVariant.
5. Повторная проверка operationId после ожидания блокировки.
6. Расчёт нового остатка и проверка invariants/переполнения long.
7. INSERT движения с уникальным operationId; проверка результата при гонке ключа на другой паре.
8. UPDATE onHand, затем аудит с состоянием до и после.
9. Общий commit либо rollback всех изменений.

DB-ограничения: onHand >= 0; reserved >= 0 и reserved <= onHand; blocked >= 0 и blocked <= onHand − reserved. Последняя форма эквивалентна reserved + blocked <= onHand, но не переполняет BIGINT при сложении. PK пары исключает второй InventoryBalance; FK проверяют справочники и связь движения с остатком.

Контракт сейчас обслуживает одну пару за вызов. Когда бизнес-сценарий меняет несколько пар, вызывающий модуль должен выполнить все вызовы в одной транзакции и в едином порядке пар (warehouseId, productVariantId). Блокировки сохраняются до окончания внешней транзакции. До реализации StockTransfer отдельного процесса перемещения и автоматической координации пары TRANSFER_OUT/TRANSFER_IN нет. Внешние сетевые вызовы не должны удерживать эту транзакцию.

## Idempotency и происхождение

operationId — обязательный стабильный UUID одного физического эффекта/строки источника. Бизнес-модуль должен повторно использовать его после double click/timeout, а не генерировать новый ключ при каждом retry. Несколько строк одного Receipt могут иметь один sourceId, но разные operationId. В пару transfer входят два отдельных ключа движения в одной внешней транзакции.

Повтор той же команды возвращает исходный StockMovement с теми же id/timestamp без новых движений и аудита. Другие warehouse/variant/type/quantity/source/actor при уже существующем ключе дают InventoryOperationConflictException. UNIQUE(operation_id) закрывает также гонки на разных парах.

sourceType/sourceId — обязательная ссылка происхождения. Inventory не проверяет существование Purchase/Sale, которых сейчас нет; это обязанность вызывающего модуля. actor передаётся доверенным backend-сценарием, не HTTP-клиентом; null означает системную операцию (в audit_entries сохраняется SYSTEM). Permissions проверяются на границе бизнес-сценария. Внутренний сервис не публикуется как обход авторизации.

## Журнал и аудит

StockMovement — immutable record, SQL-методов update/delete нет. Триггер PostgreSQL дополнительно запрещает обычные UPDATE/DELETE журнала. Исправление — новое движение с новым operationId и понятным источником. Административный TRUNCATE/DROP не является бизнес-операцией; тесты используют TRUNCATE только для изолированной тестовой БД.

Движение содержит UUID, operationId, Warehouse, ProductVariant, положительное quantity, type, occurredAt, sourceType/sourceId и actor. Отдельный audit_entries фиксирует исполнителя и snapshots. reserved/blocked не превращаются в физические движения, отдельного механизма событий для них сейчас нет.

## Миграция и проверка

V3 создаёт inventory_balances, stock_movements, индекс пагинированной истории и триггер неизменяемости. V1/V2 не изменены. Миграция не создаёт начальных остатков: для будущего ввода начального количества предусмотрен ADJUSTMENT_IN от отдельного бизнес-сценария.

Интеграционные тесты выполняются на PostgreSQL 17 Testcontainers: все типы движений, counters, доступность, FK/PK/CHECK, immutable journal, metadata, rollback внешней транзакции/аудита, idempotency, параллельные first insert/last unit/retry/cross-balance key conflict, переполнение long, обновление V2→V3 без потери данных и повторная миграция без изменений.
