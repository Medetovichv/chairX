-- Staff can register city/region delivery as part of an order, without administrative powers.
-- Grant only to the built-in EMPLOYEE role, preserving customizable RBAC.
INSERT INTO security_role_permissions(role_id,permission_code)
SELECT r.id,p.code FROM security_roles r
JOIN security_permissions p ON p.code='DELIVERIES_MANAGE'
WHERE r.code='EMPLOYEE' AND r.system_role=TRUE
ON CONFLICT DO NOTHING;
