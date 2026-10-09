INSERT INTO security_permissions (code, description)
VALUES (
           'ADMIN_ACCESS',
           'Доступ к административным функциям ChairX'
       );

INSERT INTO security_role_permissions (role_id, permission_code)
SELECT id, 'ADMIN_ACCESS'
FROM security_roles
WHERE code = 'ADMIN';