#!/usr/bin/env bash
set -euo pipefail

set -a
source .env
set +a

curl --fail --silent --show-error http://127.0.0.1:5173/ | grep -q 'ChairX'

# The frontend container proxy must reach the real Spring application.
catalog_status=$(curl --silent --output /tmp/chairx-catalog.json --write-out '%{http_code}' \
  --user "catalog:$CHAIRX_CATALOG_PASSWORD" \
  http://127.0.0.1:5173/api/products)
test "$catalog_status" = "200"

admin_status=$(curl --silent --output /tmp/chairx-admin.json --write-out '%{http_code}' \
  --user "admin:$CHAIRX_BOOTSTRAP_ADMIN_PASSWORD" \
  http://127.0.0.1:8080/api/admin/users)
test "$admin_status" = "200"

manager_status=$(curl --silent --output /tmp/chairx-manager.json --write-out '%{http_code}' \
  --user "manager:$CHAIRX_DEV_MANAGER_PASSWORD" \
  http://127.0.0.1:8080/api/admin/users)
test "$manager_status" = "403"

employee_status=$(curl --silent --output /tmp/chairx-employee.json --write-out '%{http_code}' \
  --user "employee:$CHAIRX_DEV_EMPLOYEE_PASSWORD" \
  http://127.0.0.1:8080/api/admin/users)
test "$employee_status" = "403"

roles_sql="SELECT u.username || ':' || string_agg(r.code, ',' ORDER BY r.code)
FROM app_users u
JOIN security_user_roles ur ON ur.user_id = u.id
JOIN security_roles r ON r.id = ur.role_id
GROUP BY u.username ORDER BY u.username"
roles=$(docker compose exec -T postgres psql -U chairx -d chairx -tA -c "$roles_sql")
printf '%s\n' "$roles" | grep -qx 'admin:ADMIN'
printf '%s\n' "$roles" | grep -qx 'manager:MANAGER'
printf '%s\n' "$roles" | grep -qx 'employee:EMPLOYEE'

hashes_sql="SELECT md5(string_agg(username || ':' || password_hash, '|' ORDER BY username))
FROM app_users
WHERE username IN ('admin', 'manager', 'employee')"
before=$(docker compose exec -T postgres psql -U chairx -d chairx -tA -c "$hashes_sql")

# A second app startup must not recreate users or reset hashes or privileges.
docker compose restart backend
docker compose up -d --wait --wait-timeout 180
after=$(docker compose exec -T postgres psql -U chairx -d chairx -tA -c "$hashes_sql")
test "$before" = "$after"

roles_after=$(docker compose exec -T postgres psql -U chairx -d chairx -tA -c "$roles_sql")
test "$roles" = "$roles_after"
echo "ChairX local stack: healthy, RBAC enforced, and seed remains stable after restart."

