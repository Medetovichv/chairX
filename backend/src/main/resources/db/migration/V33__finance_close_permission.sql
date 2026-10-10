INSERT INTO security_permissions(code, description)
VALUES ('FINANCE_CLOSE', 'Закрытие финансового дня');
INSERT INTO security_role_permissions(role_id, permission_code)
SELECT id, 'FINANCE_CLOSE' FROM security_roles WHERE code = 'ADMIN';
