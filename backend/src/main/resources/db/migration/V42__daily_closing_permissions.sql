-- P21: scoped daily-closing permissions, separate from global FINANCE_* rights.
INSERT INTO security_permissions (code, description) VALUES
 ('DAILY_CLOSING_READ', 'Просмотр дневных финансовых отчётов'),
 ('DAILY_CLOSING_WRITE', 'Создание и исправление дневного отчёта в разрешённое время'),
 ('DAILY_CLOSING_UNLOCK_MANAGER', 'Часовая разблокировка отчёта до конца пятого дня'),
 ('DAILY_CLOSING_UNLOCK_ADMIN', 'Часовая разблокировка отчёта любого возраста и досрочный отзыв'),
 ('DAILY_CLOSING_EXPENSE_CORRECT', 'Забытый реальный расход только для разблокированного отчёта'),
 ('DAILY_CLOSING_AUDIT_READ', 'Просмотр истории дневного отчёта')
ON CONFLICT (code) DO NOTHING;

-- Do not assign global EXPENSES_CREATE or unrestricted FINANCE_CLOSE
-- merely because staff may reconcile their daily closing.
INSERT INTO security_role_permissions (role_id, permission_code)
SELECT r.id, p.code
FROM security_roles r
CROSS JOIN security_permissions p
WHERE r.system_role = TRUE AND
 ((r.code = 'EMPLOYEE' AND p.code IN
    ('DAILY_CLOSING_READ', 'DAILY_CLOSING_WRITE', 'DAILY_CLOSING_EXPENSE_CORRECT'))
 OR (r.code = 'MANAGER' AND p.code IN
    ('DAILY_CLOSING_READ', 'DAILY_CLOSING_WRITE', 'DAILY_CLOSING_EXPENSE_CORRECT',
     'DAILY_CLOSING_UNLOCK_MANAGER', 'DAILY_CLOSING_AUDIT_READ'))
 OR (r.code = 'ADMIN' AND p.code IN
    ('DAILY_CLOSING_READ', 'DAILY_CLOSING_WRITE', 'DAILY_CLOSING_EXPENSE_CORRECT',
     'DAILY_CLOSING_UNLOCK_ADMIN', 'DAILY_CLOSING_AUDIT_READ')))
ON CONFLICT (role_id, permission_code) DO NOTHING;
