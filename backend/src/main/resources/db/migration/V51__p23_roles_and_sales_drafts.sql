-- P23: role concurrency and a narrowly scoped operational permission.
-- Existing IDs, users, role assignments, audit and all older migrations are preserved.
ALTER TABLE security_roles ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE security_roles ADD CONSTRAINT security_roles_version_nonnegative CHECK (version >= 0);

INSERT INTO security_permissions(code, description)
VALUES ('SALES_DRAFT_MANAGE', 'Редактирование и подтверждение черновиков продаж')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions(role_id, permission_code)
SELECT id, 'SALES_DRAFT_MANAGE' FROM security_roles
WHERE system_role = TRUE AND code IN ('ADMIN', 'MANAGER', 'EMPLOYEE')
ON CONFLICT (role_id, permission_code) DO NOTHING;

-- ADMIN remains the effective all-permissions system role after upgrades.
INSERT INTO security_role_permissions(role_id, permission_code)
SELECT r.id, p.code FROM security_roles r CROSS JOIN security_permissions p
WHERE r.code = 'ADMIN' AND r.system_role = TRUE
ON CONFLICT (role_id, permission_code) DO NOTHING;
