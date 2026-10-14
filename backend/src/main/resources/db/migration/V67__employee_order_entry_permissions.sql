-- Staff must be able to find the actual chair variant and identify customers
-- by their WhatsApp phone when entering an order. No catalogue mutation, no
-- unrestricted finance/expense read and no customer deletion permissions.
INSERT INTO security_role_permissions(role_id,permission_code)
SELECT r.id,p.code
FROM security_roles r
JOIN security_permissions p ON p.code IN (
  'CATALOG_READ','CUSTOMERS_READ','CUSTOMERS_CREATE'
)
WHERE r.system_role=TRUE AND r.code='EMPLOYEE'
ON CONFLICT (role_id,permission_code) DO NOTHING;
