-- Operational staff may record payments received and their own expenses.
-- No FINANCE_READ, EXPENSES_READ, PAYMENTS_CANCEL, REFUNDS_CREATE or finance admin access.
INSERT INTO security_role_permissions(role_id,permission_code)
SELECT r.id,p.code FROM security_roles r
JOIN security_permissions p ON p.code IN ('PAYMENTS_READ','PAYMENTS_CREATE','EXPENSES_CREATE')
WHERE r.code='EMPLOYEE' AND r.system_role=TRUE
ON CONFLICT DO NOTHING;
