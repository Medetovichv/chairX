# ChairX — контроль изменений и итоговая проверка

Дата: 10 октября 2026 года, Europe/Berlin.

Итог: исправления проверены полным `mvn clean verify`: **528 тестов, 0 failures, 0 errors, 0 skipped**, 59 тестовых классов. Это подтверждение работоспособности проверенных сценариев, а не доказательство отсутствия всех ошибок. Готовность к эксплуатации не подтверждена: RBAC бизнес-маршрутов отложен по решению владельца; исторические финансовые данные требуют сверки. Полный Definition of Done аудита не объявляется закрытым.

## A. Исходное состояние и фактическая архитектура

Репозиторий: `Medetovichv/chairX`. Локальный исходный checkout: `/Users/medetovich/Projects/chairx`. Работа выполнена в изолированной копии в `work/chairx` текущего чата, на ветке `audit/full-backend-stabilization` от `feat/daily-finance-closing`.

Базовый SHA: `e7ccb514b113691cc4f881a40c1a1b8e92cb868e`. Сверка `git ls-remote` подтвердила тот же SHA ветки на GitHub. SHA main до работ: `34991ea4c2e35827c379cd84c70e3d0267f1b5ab`; GitHub main имеет тот же SHA. В исходном checkout был неотслеживаемый файл `main`; его содержимое не изменялось.

Фактические версии при проверке: Java 25.0.4.1, Maven 3.9.16, Spring Boot 4.1.1, PostgreSQL 17.11 в Testcontainers. Версии зависимостей не обновлялись. В production resources исходно 33 Flyway migrations, после исправления — 34. Дополнительные тестовые миграции, создаваемые существующими тестами, не являются production-схемой.

Архитектура — модульный монолит Spring Boot. HTTP controllers вызывают application services; транзакции охватывают документы, складские изменения и аудит. Справочники используют JPA и optimistic locking, операционные модули преимущественно JdbcClient/JdbcTemplate и row locks. PostgreSQL защищает FK, CHECK, уникальность операций и неизменяемость части складских журналов. Flyway управляет схемой; Hibernate выполняет validate, Open Session in View выключен.

| Область | Проверенные границы и сценарии |
|---|---|
| Товары, варианты, склады, поставщики, клиенты | Валидация, стабильные идентификаторы, активация/деактивация, связи, изменение справочников без удаления исторических документов; существующие тесты API и persistence |
| Закупки и приёмка | Блокировка закупки, ограничения количества, фиксация стоимости, частичная приёмка, повтор ключа; ReceivePurchase связывает документ, движение и FIFO в одной транзакции |
| Inventory и FIFO | available = onHand − reserved − blocked; защита количества и себестоимости, операции перемещения и возврата; единый порядок warehouse locks |
| Продажи | Резервирование, выдача, отмена, snapshot unitSalePrice, replay idempotency; отдельная граница для доставки |
| Доставка, возвраты, дефекты | Переходы состояний, выдача/возврат товара, блокировка повреждённых единиц, восстановление FIFO; существующие boundary/concurrency/rollback tests |
| Оплаты, refunds, обмены, расходы | Ограничения суммы, блокировки оплаты/обмена, атомарные новые финансовые проводки |
| Финансы и закрытие дня | CASH/BANK, инициализация, transfer locks BANK → CASH, закрытие по Asia/Bishkek, защита закрытого дня и rollback |
| Security и admin | HTTP Basic, CSRF, RBAC финансов/admin, password hashing, bootstrap, защита последнего администратора; бизнес-RBAC остаётся незавершённым |

Проверка охватывает ключевые application/persistence границы и существующий полный набор тестов. Полный независимый построчный аудит каждого DTO, SQL-запроса и всех возможных комбинаций конкурентных операций не заявляется выполненным.

## B–C. Подтверждённые проблемы и исправления

### F1 — CRITICAL: финансовые документы не изменяли CASH/BANK

Причина: PaymentService, RefundService, ExpenseService и ExchangeSettlementService записывали собственные документы, но не вызывали изменение finance_accounts и запись finance_movements. Среди production Java-путей остаток меняли только opening balance и transfer; SQL-триггеров для связи этих документов с финансами не было. DailyClosingService снимал snapshot finance_accounts, поэтому оплаты/расходы не попадали в его ожидаемые остатки.

После подтверждения владельцем добавлена граница FinancePostingService с Propagation.MANDATORY. Она блокирует счёт, проверяет инициализацию, изменяет баланс через существующую защиту закрытого дня/неотрицательного остатка и добавляет journal entry. Исходный документ и проводка откатываются вместе. CASH → CASH, TRANSFER → BANK, expense BANK → BANK.

Оплата зачисляет средства; отмена оплаты создаёт отдельную обратную проводку; refund и expense списывают; exchange IN зачисляет, OUT списывает. Повторные refund/settlement и отмены возвращаются до повторной проводки. Нулевая оплата сохраняет существующее поведение документа и не создаёт нулевой journal entry.

Для обратной проводки введён source_type PAYMENT_REVERSAL: старый unique(source_type, source_id, account_code) не позволяет использовать PAYMENT повторно. V34 расширяет разрешённые источники, сохраняя уникальность и старые данные. Journal created_at использует clock_timestamp(), чтобы время проводки не равнялось началу долго ожидающей транзакции.

Исторические документы автоматически не перепроводятся. Отмена старой положительной оплаты без исходной PAYMENT-проводки отклоняется с конфликтом и требует сверки. Это предотвращает необоснованное списание из начального остатка; для реальных старых данных нужен согласованный план перехода.

Проверено: bank credit/reversal exactly once; rollback оплаты при неинициализированном счёте; expense debit и rollback при недостатке; refund replay и failed debit; exchange credit replay и сохранение pending state при failed OUT; отказ расхода после закрытия; гонка expense/closing. Полный набор связанных модулей также прошёл.

### F2 — HIGH: несовместимый порядок складских блокировок

Причина: InventoryTransferService сортировал UUID естественным порядком Java, а SaleService — по строковому UUID. Java сравнивает старшую часть UUID как signed long. Для пары `10000000-…` и `f0000000-…` порядки противоположны, что создаёт условия взаимной блокировки transfer и multi-warehouse sale.

Исправление: transfer использует строковый порядок, совпадающий с продажами. Тест проверяет первые реальные обращения к lockOrCreate на PostgreSQL и корректный перенос количества/FIFO. На исходном компараторе этот тест падает. Он подтверждает нарушение lock protocol; конкретный cross-module deadlock не воспроизводился отдельным принудительно синхронизированным тестом. Существующие concurrency tests прошли.

### F3 — MEDIUM: неоднозначные fingerprints возвратов

Причина: текстовые поля RefundService и ReturnService соединялись символом `|`. Например, reason=`A|B`, reference=`C` и reason=`A`, reference=`B|C` имеют одинаковое каноническое представление. Это коллизия сериализации до SHA-256, а не криптографическая коллизия.

Исправление: replay дополнительно сравнивает структурированные сохранённые поля, суммы и позиции с запросом. Старый алгоритм и сохранённые fingerprints остаются совместимыми. Разные payloads теперь отклоняются; новые документы/проводки не создаются. Отдельные regression tests для refunds и returns падают на исходном коде и проходят после исправления.

### F4 — MEDIUM: бизнес-ошибки закрытия попадали в HTTP 500

Причина: ошибки суммы/даты, повторное закрытие и отсутствие closing использовали общие IllegalArgumentException/IllegalStateException, которые попадали в общий unexpected handler. Старые HTTP tests проверяли malformed JSON/date, но не эти business exceptions.

Исправление: типизированные исключения закрытия сохраняют наследование прежних исключений для service callers. API возвращает 400 для неверных business inputs, 409 для конфликта состояния, 404 для отсутствующего closing. Финансовые posting conflicts также возвращают 409 с безопасным сообщением. DTO, маршруты и успешные response shapes не менялись.

### F5 — HIGH, оставлено: RBAC бизнес-маршрутов

В V29 заданы SALES_* и PURCHASE_*, но соответствующие controllers проходят через anyRequest().authenticated(). Методных permission checks не найдено. Это позволяет авторизованному техническому catalog и сотруднику без соответствующего разрешения обращаться к бизнес-операциям. FINANCE_* и административные маршруты имеют отдельные restrictions.

Владелец явно выбрал «Зафиксировать RBAC как незавершённый этап». Исправления RBAC не выполнялись. Это блокирующее ограничение для реальной эксплуатации с сотрудниками.

## Контроль бизнес-правил, REST API и схемы

Подтверждённые владельцем изменения поведения:

- Денежные документы теперь действительно влияют на счёт согласно указанному способу оплаты.
- Для новой денежной проводки требуется инициализированный соответствующий счёт; недостаток средств или закрытый день откатывает весь документ.
- Отмена положительной оплаты возвращает зачисление обратной проводкой; старую непроведённую оплату нельзя автоматически списать.

Существующая семантика закрытия сохранена: закрытие блокирует новые денежные изменения до конца текущего дня по Бишкеку; следующий день не блокируется записью предыдущего. Actual balance фиксируется как результат пересчёта, но не корректирует balance автоматически. Причина расхождения обязательна. Различие expenseDate и фактического времени проводки не переопределялось.

Рассрочка отсутствует в PaymentMethod/текущем контракте; новый workflow не добавлялся. SKU uniqueness, статусы закупок/продаж/доставок, способы резервирования и FIFO не переопределялись.

REST: маршруты и DTO сохранены. Новое ограничение состояния денежных документов — HTTP 409. Closing business validation — 400, duplicate/uninitialized closing — 409, absent closing — 404. Клиенту необходимо учитывать эти корректные error responses вместо прежнего 500.

БД: добавлена только V34__allow_payment_reversal_source.sql; V1–V33 не менялись. Таблицы/столбцы не удалены, historical rows/balances не пересчитывались. Проверены clean schema migration, upgrade применённой V33 → V34 с сохранением баланса/старого journal entry, validate и повторный migrate без новых изменений. Это не тест миграции копии реальной production БД.

## D. Результаты проверок

1. Исходный `mvn clean verify`: 514 тестов, failures/errors/skipped = 0.
2. Первый запуск после интеграции: 514 тестов, 1 failure — ProductPersistenceTests ожидал ровно 33 миграции. Ожидание обновлено до 34 после добавления V34; проверка не удалялась и не ослаблялась.
3. Промежуточная сборка с основными regression tests: 523 теста, failures/errors/skipped = 0.
4. Пять выбранных regression tests на исходных реализациях: tests=5, failures=5, errors=0, skipped=0. Проверенные причины: отсутствие payment credit, отсутствие expense debit, неоднозначные refund/return payloads и порядок inventory locks. После запуска все исправленные исходники автоматически восстановлены.
5. Окончательный `mvn clean verify` на коде третьего audit-коммита: **528 тестов, failures=0, errors=0, skipped=0; BUILD SUCCESS**. Выполнены compilation, все Surefire tests, jar packaging и Spring Boot repackage. Финальный отчёт добавляет только документацию.

Сводка получена независимо из 59 Surefire XML; подробности находятся в test-results.json и verification-summary.txt. Новых tests — 14; существующие assertions сохранились. FundedFinanceExtension явно подготавливает начальные средства для прежних workflow tests и после каждого теста восстанавливает accounts и удаляет созданные finance movements; production behavior не обходится.

Не выполнено: применение к реальной БД, сверка исторических денежных документов, отдельная внешняя security assessment, длительное нагрузочное тестирование, реальный переход через полночь под удерживаемой блокировкой, исчерпывающий перебор межмодульных конкурентных комбинаций. Системные Java/PostgreSQL clocks должны быть синхронизированы; drift не симулировался. Maven verify не запускает отсутствующие в pom внешние quality gates.

## E. Оставшиеся ограничения

- HIGH: бизнес-RBAC отложен владельцем.
- HIGH для перехода существующей установки: первоначальные balances и старые payments/refunds/expenses/settlements требуют сверки; V34 намеренно не выполняет автоматическую backfill. Наличие/объём реальных исторических расхождений не проверены.
- Expense create не имеет клиентского idempotency key. Повтор HTTP create формирует новый документ и новую проводку. Проводка одного документа атомарна, но одинаковые независимые create requests не считаются replay. Не введено выдуманное правило запрета одинаковых реальных расходов; требуется согласовать retry contract перед эксплуатацией.
- Cash-flow агрегирует исходные документы и expenseDate; journal отражает posting time. Это разные отчётные оси, включая backdated/future expenseDate. Исторические cash-flow данные не становятся автоматически корректными closing snapshots.
- Finance opening/transfer services существуют, но соответствующих REST controllers не найдено; требуется определить путь эксплуатации/инициализации. Это незавершённая API-функциональность, а не реализованный endpoint, который прошёл проверку.
- README описывает ранний Foundation и местами не соответствует текущим реализованным модулям; production инструкции нужно актуализировать.

Отсутствие критических проблем во всей системе не доказано. Подтверждённый разрыв новых денежных проводок исправлен; критические области production migration/reconciliation остаются непроверенными.

## F–H. Оценка архитектуры и следующие шаги

| Направление | Оценка |
|---|---|
| Modularity | Подходит для модульного монолита; часть cross-module repository dependencies сохраняется, массовый рефакторинг не нужен |
| Data integrity | Сильные DB invariants и rollback tests; новые деньги атомарны, историческая сверка обязательна |
| Security | Finance/admin защищены; business RBAC пока недостаточен для эксплуатации |
| Testability | Хорошая PostgreSQL-based integration база; проверены 528 tests, включая regression и concurrency |
| Maintainability | Исправления локальны; документирование текущего этапа нуждается в обновлении |
| Масштаб текущего бизнеса | Архитектура соответствует небольшому бизнесу; нагрузочная производительность отдельно не измерялась |

Backend можно использовать как проверенную основу для следующего этапа разработки **после отдельного решения владельца**. Готовность к реальной эксплуатации с сотрудниками не подтверждается. Техническая сборка не означает готовность всего продукта.

Обязательно до первого запуска: включить согласованные бизнес-permissions; сверить исторические финансы и начальные остатки; согласовать безопасный retry contract расходов; определить процесс инициализации/переводов; проверить migration на копии реальных данных, backup/restore, HTTPS и секреты окружения.

Желательно после первого запуска: обновить operational docs, добавить наблюдение за отказами/deadlocks, пройти реальный midnight сценарий и согласовать объяснение cash-flow/document-date против posting-time.

Можно отложить: CRM, WhatsApp/Instagram, дополнительные отчёты, инфраструктурные усложнения и оптимизацию под большой масштаб. Эти функции в данном аудите не реализовывались.

## Git и список изменений

Все исправления сохранены в отдельной audit-ветке. Merge, force push, удаление веток и PR не выполнялись. Audit-ветка не объединялась с main или feat/daily-finance-closing. Рабочие файлы исходного checkout не перезаписывались.

Коммиты кода:
- `fc1535730159baa7330d04de82159b8dd46db264` — fix(finance): post business documents atomically to account balances
- `aea03fea172ac760d23b4d1450c8179c464d6c78` — fix: reject ambiguous idempotency payloads for returns and refunds
- `be42d6bd599e2c4573ea3d1c41ebf686553671cb` — fix(inventory): align transfer locks with sale warehouse ordering

Последний отдельный коммит добавляет этот отчёт; его полный SHA указан в change-manifest.json и итоговом отчёте outputs.

Все созданные/изменённые tracked-файлы относительно базового SHA (`A` — создан, `M` — изменён):

- `M — backend/src/main/java/kg/chairx/common/web/ApiExceptionHandler.java`
- `M — backend/src/main/java/kg/chairx/exchange/application/ExchangeSettlementService.java`
- `M — backend/src/main/java/kg/chairx/expense/application/ExpenseService.java`
- `A — backend/src/main/java/kg/chairx/finance/application/ClosingNotFoundException.java`
- `M — backend/src/main/java/kg/chairx/finance/application/DailyClosingService.java`
- `A — backend/src/main/java/kg/chairx/finance/application/FinanceConflictException.java`
- `A — backend/src/main/java/kg/chairx/finance/application/FinancePostingException.java`
- `A — backend/src/main/java/kg/chairx/finance/application/FinancePostingService.java`
- `A — backend/src/main/java/kg/chairx/finance/application/FinanceValidationException.java`
- `A — backend/src/main/java/kg/chairx/finance/domain/FinanceAccountOperationException.java`
- `M — backend/src/main/java/kg/chairx/finance/persistence/FinanceAccountRepository.java`
- `M — backend/src/main/java/kg/chairx/finance/persistence/FinanceMovementRepository.java`
- `M — backend/src/main/java/kg/chairx/inventory/application/InventoryTransferService.java`
- `M — backend/src/main/java/kg/chairx/payment/application/PaymentService.java`
- `M — backend/src/main/java/kg/chairx/refund/application/RefundService.java`
- `M — backend/src/main/java/kg/chairx/returning/application/ReturnService.java`
- `A — backend/src/main/resources/db/migration/V34__allow_payment_reversal_source.sql`
- `A — backend/src/test/java/kg/chairx/FundedFinanceExtension.java`
- `M — backend/src/test/java/kg/chairx/exchange/ExchangeCreationTests.java`
- `M — backend/src/test/java/kg/chairx/exchange/ExchangeSettlementTests.java`
- `M — backend/src/test/java/kg/chairx/exchange/ExchangeWorkflowTests.java`
- `M — backend/src/test/java/kg/chairx/expense/ExpenseTests.java`
- `M — backend/src/test/java/kg/chairx/finance/DailyClosingIntegrationTest.java`
- `A — backend/src/test/java/kg/chairx/finance/FinanceMigrationUpgradeTests.java`
- `A — backend/src/test/java/kg/chairx/finance/FinancialDocumentClosingTests.java`
- `M — backend/src/test/java/kg/chairx/inventory/application/InventoryTransferTests.java`
- `M — backend/src/test/java/kg/chairx/inventory/cost/FifoIntegrityTests.java`
- `M — backend/src/test/java/kg/chairx/payment/PaymentApiTests.java`
- `M — backend/src/test/java/kg/chairx/payment/PaymentRefundIntegrityTests.java`
- `M — backend/src/test/java/kg/chairx/payment/PaymentTests.java`
- `M — backend/src/test/java/kg/chairx/product/ProductPersistenceTests.java`
- `M — backend/src/test/java/kg/chairx/refund/RefundApiTests.java`
- `M — backend/src/test/java/kg/chairx/refund/RefundCompensationTests.java`
- `M — backend/src/test/java/kg/chairx/refund/RefundConcurrencyTests.java`
- `M — backend/src/test/java/kg/chairx/refund/RefundTests.java`
- `M — backend/src/test/java/kg/chairx/returning/ReturnTests.java`
- `A — docs/audit/2026-10-10-final-verification.md`

Новые и изменённые тестовые файлы — все пути `backend/src/test/…` в этом списке. В том числе новые FinanceMigrationUpgradeTests, FinancialDocumentClosingTests и test fixture FundedFinanceExtension. Полный машинный список изменений и SHA содержится в change-manifest.json.

Пользовательские deliverables outputs: ChairX-final-audit-report.md, change-manifest.json, test-results.json, verification-summary.txt, ChairX-audit.patch, ChairX-audit.bundle. Временные скрипты, логи и Maven target остаются в work/ и не являются изменениями ChairX.

Работа остановлена после отчёта; объединение веток и разработка новых модулей требуют подтверждения владельца.
