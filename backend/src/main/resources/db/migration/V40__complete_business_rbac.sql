-- P17 business RBAC. Extends the original database-backed permission model.
-- Do not reset users, roles, grants, passwords or security audit history.
INSERT INTO security_permissions (code, description)
VALUES
    ('CATALOG_READ','Просмотр каталога и вариантов'),
    ('CATALOG_MANAGE','Изменение каталога и цен'),
    ('SUPPLIERS_READ','Просмотр поставщиков'),
    ('SUPPLIERS_MANAGE','Управление поставщиками'),
    ('CUSTOMERS_READ','Просмотр клиентов'),
    ('CUSTOMERS_CREATE','Создание клиентов'),
    ('CUSTOMERS_MANAGE','Изменение клиентов'),
    ('WAREHOUSES_MANAGE','Изменение справочника складов'),
    ('PURCHASE_UPDATE','Редактирование закупок и карго'),
    ('PURCHASE_CANCEL','Отмена закупок'),
    ('PURCHASE_PAYMENTS_READ','Просмотр оплат поставщикам'),
    ('PURCHASE_PAYMENTS_CREATE','Оплата закупок из кассы или банка'),
    ('DELIVERIES_READ','Просмотр доставок'),
    ('DELIVERIES_MANAGE','Отгрузка, доставка и возврат неуспешной отправки'),
    ('RETURNS_READ','Просмотр возвратов товаров'),
    ('RETURNS_CREATE','Оформление возврата товара'),
    ('PAYMENTS_READ','Просмотр оплат продаж'),
    ('PAYMENTS_CREATE','Регистрация оплат продаж'),
    ('PAYMENTS_CANCEL','Отмена поступившей оплаты'),
    ('REFUNDS_READ','Просмотр денежных возвратов'),
    ('REFUNDS_CREATE','Возврат денег клиенту'),
    ('EXCHANGES_READ','Просмотр обменов'),
    ('EXCHANGES_CREATE','Создание обмена'),
    ('EXCHANGES_SETTLE','Финансовое урегулирование обмена'),
    ('EXPENSES_READ','Просмотр расходов'),
    ('EXPENSES_CREATE','Списание денег на расходы'),
    ('DEFECTS_READ','Просмотр брака'),
    ('DEFECTS_MANAGE','Обработка брака без списания'),
    ('DEFECTS_WRITE_OFF','Списание брака со склада')
ON CONFLICT (code) DO NOTHING;

-- Existing V29/V30/V33 permissions already belong to ADMIN.
-- V40 explicitly grants ONLY the newly introduced permissions.
INSERT INTO security_role_permissions (role_id, permission_code)
SELECT r.id, p.code
FROM security_roles r
JOIN security_permissions p ON p.code IN ('CATALOG_READ', 'CATALOG_MANAGE', 'SUPPLIERS_READ', 'SUPPLIERS_MANAGE', 'CUSTOMERS_READ', 'CUSTOMERS_CREATE', 'CUSTOMERS_MANAGE', 'WAREHOUSES_MANAGE', 'PURCHASE_UPDATE', 'PURCHASE_CANCEL', 'PURCHASE_PAYMENTS_READ', 'PURCHASE_PAYMENTS_CREATE', 'DELIVERIES_READ', 'DELIVERIES_MANAGE', 'RETURNS_READ', 'RETURNS_CREATE', 'PAYMENTS_READ', 'PAYMENTS_CREATE', 'PAYMENTS_CANCEL', 'REFUNDS_READ', 'REFUNDS_CREATE', 'EXCHANGES_READ', 'EXCHANGES_CREATE', 'EXCHANGES_SETTLE', 'EXPENSES_READ', 'EXPENSES_CREATE', 'DEFECTS_READ', 'DEFECTS_MANAGE', 'DEFECTS_WRITE_OFF')
WHERE r.code='ADMIN' AND r.system_role=TRUE
ON CONFLICT (role_id,permission_code) DO NOTHING;

-- Operational permissions. Deliberately NO payout, refund, money reversal,
-- day closing, finance transfer, exchange settlement or defect write-off.
INSERT INTO security_role_permissions (role_id,permission_code)
SELECT r.id,p.code FROM security_roles r
JOIN security_permissions p ON p.code IN ('CATALOG_READ', 'CATALOG_MANAGE', 'SUPPLIERS_READ', 'SUPPLIERS_MANAGE', 'CUSTOMERS_READ', 'CUSTOMERS_CREATE', 'CUSTOMERS_MANAGE', 'PURCHASE_UPDATE', 'PURCHASE_CANCEL', 'PURCHASE_PAYMENTS_READ', 'DELIVERIES_READ', 'DELIVERIES_MANAGE', 'RETURNS_READ', 'RETURNS_CREATE', 'PAYMENTS_READ', 'PAYMENTS_CREATE', 'REFUNDS_READ', 'EXCHANGES_READ', 'EXCHANGES_CREATE', 'EXPENSES_READ', 'DEFECTS_READ', 'DEFECTS_MANAGE')
WHERE r.code='MANAGER' AND r.system_role=TRUE
ON CONFLICT (role_id,permission_code) DO NOTHING;

-- Frontline employee gets reading and limited creation, never cash out.
INSERT INTO security_role_permissions (role_id,permission_code)
SELECT r.id,p.code FROM security_roles r
JOIN security_permissions p ON p.code IN ('CATALOG_READ', 'CUSTOMERS_READ', 'CUSTOMERS_CREATE', 'DELIVERIES_READ')
WHERE r.code='EMPLOYEE' AND r.system_role=TRUE
ON CONFLICT (role_id,permission_code) DO NOTHING;

-- Existing manager grants lack PURCHASE_CONFIRM (though confirmation is
-- part of this role's operations). Add existing permission without recreating.
INSERT INTO security_role_permissions (role_id,permission_code)
SELECT r.id,'PURCHASE_CONFIRM' FROM security_roles r
WHERE r.code='MANAGER' AND r.system_role=TRUE
ON CONFLICT (role_id,permission_code) DO NOTHING;
