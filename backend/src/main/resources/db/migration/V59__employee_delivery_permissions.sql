-- Separate delivery creation from dispatch, status changes and cancellation.
INSERT INTO security_permissions(code,description)
VALUES ('DELIVERIES_CREATE','Создание доставки при оформлении продажи')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions(role_id,permission_code)
SELECT r.id,'DELIVERIES_CREATE' FROM security_roles r
WHERE r.system_role=TRUE AND r.code IN ('ADMIN','MANAGER','EMPLOYEE')
ON CONFLICT DO NOTHING;
