-- F12: narrowly scoped lifecycle authorities, no other role assignments touched.
INSERT INTO security_permissions(code, description)
VALUES ('USERS_ACTIVATE', 'Повторная активация сотрудника'),
       ('USERS_RESET_PASSWORD', 'Сброс пароля сотрудника')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions(role_id, permission_code)
SELECT r.id, p.code
FROM security_roles r
CROSS JOIN security_permissions p
WHERE r.code = 'ADMIN'
  AND r.system_role = TRUE
  AND p.code IN ('USERS_ACTIVATE', 'USERS_RESET_PASSWORD')
ON CONFLICT (role_id, permission_code) DO NOTHING;
