-- P21: ADMIN is the superuser role and receives every seeded permission.
-- Preserve the distinction between administrative and managerial grant policies:
-- the application checks DAILY_CLOSING_UNLOCK_ADMIN first, so giving ADMIN
-- this permission does NOT subject administrators to the five-day cutoff.
INSERT INTO security_role_permissions (role_id, permission_code)
SELECT r.id, 'DAILY_CLOSING_UNLOCK_MANAGER'
FROM security_roles r
WHERE r.code = 'ADMIN' AND r.system_role = TRUE
ON CONFLICT (role_id, permission_code) DO NOTHING;
