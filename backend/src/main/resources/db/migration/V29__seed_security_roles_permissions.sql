-- 1. Базовые разрешения
INSERT INTO security_permissions (code, description) VALUES
                                                         ('FINANCE_READ', 'Просмотр финансовых счетов и операций'),
                                                         ('FINANCE_TRANSFER', 'Переводы между финансовыми счетами'),
                                                         ('FINANCE_INITIALIZE', 'Установка начальных остатков'),

                                                         ('USERS_READ', 'Просмотр сотрудников'),
                                                         ('USERS_CREATE', 'Создание сотрудников'),
                                                         ('USERS_UPDATE', 'Редактирование сотрудников'),
                                                         ('USERS_DEACTIVATE', 'Деактивация сотрудников'),

                                                         ('ROLES_READ', 'Просмотр ролей'),
                                                         ('ROLES_CREATE', 'Создание ролей'),
                                                         ('ROLES_UPDATE', 'Изменение ролей и их разрешений'),
                                                         ('ROLES_ASSIGN', 'Назначение ролей сотрудникам'),

                                                         ('SALES_READ', 'Просмотр продаж'),
                                                         ('SALES_CREATE', 'Создание продаж'),
                                                         ('SALES_UPDATE', 'Редактирование продаж'),
                                                         ('SALES_CANCEL', 'Отмена продаж'),

                                                         ('INVENTORY_READ', 'Просмотр складских остатков'),
                                                         ('INVENTORY_RECEIVE', 'Приёмка товаров'),
                                                         ('INVENTORY_TRANSFER', 'Перемещение товаров между складами'),

                                                         ('PURCHASE_READ', 'Просмотр закупок'),
                                                         ('PURCHASE_CREATE', 'Создание закупок'),
                                                         ('PURCHASE_CONFIRM', 'Подтверждение закупок');

-- 2. Системные роли
INSERT INTO security_roles (id, code, name, system_role)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'ADMIN', 'Администратор', TRUE),
    ('00000000-0000-0000-0000-000000000002', 'MANAGER', 'Менеджер', TRUE),
    ('00000000-0000-0000-0000-000000000003', 'EMPLOYEE', 'Сотрудник', TRUE);

-- 3. ADMIN получает все существующие разрешения
INSERT INTO security_role_permissions (role_id, permission_code)
SELECT r.id, p.code
FROM security_roles r
         CROSS JOIN security_permissions p
WHERE r.code = 'ADMIN';

-- 4. MANAGER получает базовые разрешения
INSERT INTO security_role_permissions (role_id, permission_code)
SELECT r.id, p.code
FROM security_roles r
         CROSS JOIN security_permissions p
WHERE r.code = 'MANAGER'
  AND p.code IN (
                 'FINANCE_READ',
                 'SALES_READ',
                 'SALES_CREATE',
                 'SALES_UPDATE',
                 'INVENTORY_READ',
                 'INVENTORY_RECEIVE',
                 'INVENTORY_TRANSFER',
                 'PURCHASE_READ',
                 'PURCHASE_CREATE'
    );

-- 5. EMPLOYEE получает ограниченный доступ
INSERT INTO security_role_permissions (role_id, permission_code)
SELECT r.id, p.code
FROM security_roles r
         CROSS JOIN security_permissions p
WHERE r.code = 'EMPLOYEE'
  AND p.code IN (
                 'SALES_READ',
                 'SALES_CREATE',
                 'INVENTORY_READ'
    );