# ChairX F12 — Complete Employee & Role Administration

## Scope and baseline
Built on the current `main` after F11. This package implements the
higher-priority *Full Employee Lifecycle & Dynamic RBAC* amendment. Unlike the
original frontend-only F12 specification, this package includes narrowly scoped
backend and Flyway changes for activation/password reset. P23/F02 RBAC,
HTTP Basic, CSRF, app_users, security_roles, security_user_roles, audit log
and all existing business modules remain intact.

## User lifecycle
- `GET /api/admin/users`, `GET /api/admin/users/{id}`: real server users,
  active and inactive, role codes; response never contains password or hash.
- `POST /api/admin/users`: login (3–100 chars, a-z/0-9/._-), display name
  (1–200), password (12–128). Username normalized to lowercase. Reserved
  technical login rejected by backend. No automatic roles.
- `PATCH /api/admin/users/{id}/deactivate`: existing transaction; last
  active ADMIN cannot be deactivated.
- **New** `PATCH /api/admin/users/{id}/activate`: existing UUID is made
  active, prior roles and history retained. Transactional idempotence: an
  already active account produces no additional audit event.
- **New** `POST /api/admin/users/{id}/reset-password` with JSON
  `{"newPassword":"..."}`: password length 12–128. Existing
  `PasswordEncoderFactories.createDelegatingPasswordEncoder()` produces
  bcrypt hash persisted to `app_users.password_hash`. A successful response
  is **204 No Content**. No password, password hash or password history is
  returned or written to security audit; only `USER_PASSWORD_RESET`
  and actor + user UUID are recorded. A password change invalidates old
  HTTP Basic credentials on next request. No delivery of credentials by
  email, WhatsApp or another service.
- On resetting own password, frontend signs out, clears private Query cache,
  and requires login with the new password.

## Administrative grants and separation
Every admin endpoint has **both** a verified active system ADMIN role
(`security_roles.code = 'ADMIN' AND system_role = TRUE`) **and** the
corresponding permission. Merely obtaining an admin-looking custom role or
an admin-only permission cannot confer administrator status. Server checks
apply even if client-side buttons are manipulated.

New permissions in **Flyway V52__employee_lifecycle_permissions.sql**:
- `USERS_ACTIVATE`: PATCH activation
- `USERS_RESET_PASSWORD`: POST password reset

V52 grants them to **system ADMIN only** (no MANAGER/EMPLOYEE/custom grant,
no reset of existing users, passwords, roles or audit). Other permissions
`USERS_READ`, `USERS_CREATE`, `USERS_DEACTIVATE`,
`ROLES_READ`, `ROLES_CREATE`, `ROLES_UPDATE`, `ROLES_ASSIGN`
remain unchanged.

## Roles and permissions
- `GET /api/admin/roles`, `GET /api/admin/roles/{id}`,
  `POST /api/admin/roles`, `PUT /api/admin/roles/{id}`.
- `GET /api/admin/permissions`: server-provided codes, descriptions,
  groups, sensitive/adminOnly; UI groups and searches dynamically.
- Role code `[A-Z][A-Z0-9_]{2,99}`, name 1–200 chars, no ADMIN/MANAGER/
  EMPLOYEE reserved code. Custom role names are user-supplied, **never
  predefined** (Кладовщик, Кассир, Курьер are examples, not seeded roles).
- `ADMIN_ACCESS`, `USERS_*`, `ROLES_*`,
  `DAILY_CLOSING_UNLOCK_ADMIN`, `CATALOG_ACCESS` may not be assigned
  to custom roles. Backend also enforces this.
- Role assignment: `POST /api/admin/users/{id}/roles` with
  `{"roleId":"UUID"}`; role removal:
  `DELETE /api/admin/users/{id}/roles/{roleId}`. One user can hold
  multiple roles, and removing one does not remove others. The business
  permissions of all current roles are unioned in backend.
- Editing a role sends `name`, explicit permission code array,
  `reason` (required, <=500 chars), and the server's current
  `expectedVersion`. `VERSION_CONFLICT` (HTTP 409) requires
  re-reading the role; the UI retains unsaved selections and never silently
  retries or merges. System ADMIN is immutable, and backend protects the
  last active administrator from role removal/deactivation.
- Editing role definitions forces the active administrator to reauthenticate
  (conservative protection against stale /api/auth/me permissions). Editing
  one's own role assignments or deactivating oneself also signs out.

## Pages and guards
- `/admin`: redirect to first authorized child.
- `/admin/users`: list, client-side search/filter, create (no server
  pagination contract).
- `/admin/users/:userId`: user details, activity, assigned roles, individual
  role changes, activate/deactivate and password reset with confirmation.
- `/admin/roles`: system/custom role list, create role.
- `/admin/roles/:roleId`: role detail + version-guarded editing.
- `/admin/audit`: server pagination (20/page), targetId filter, raw action
  codes displayed as safe text; no HTML injection.

The existing desktop and mobile sidebar are reused. Frontend admin route
guards check effective permissions **and** system ADMIN role in /api/auth/me;
backend SecurityConfig remains authoritative. No new AuthProvider, JWT,
OAuth or persisted credentials.

## Errors and safety
Mutations use the existing F02 ApiClient and CSRF. A 401 clears the
in-memory auth session and private query cache. Server-side 400/403/404/409
remain authoritative. Creating employees and roles on an uncertain network
response is **not** retried blindly; the UI requires checking the existing
server list. User creation never automatically repeats after a failed role
assignment; role assignment is a separate operation.

## Tests and rollout
Backend integration tests use Testcontainers PostgreSQL 17 with database
name `chairx_test`, guarded from accidental production connections.
The original P23 V50→V51 migration regression is pinned to V51, so the new
V52 migration cannot invalidate a V51-only test.

Commands:
```sh
cd backend && mvn clean verify
cd ../frontend && npm ci && npm run lint && npm run typecheck && npm run test && npm run build
cd .. && docker compose up -d --build --wait
```
Final verification status is reported in the PR and GitHub Actions. Do not
merge without explicit authorization. These endpoints do **not** provide
session revocation for already authenticated sessions independent of HTTP
Basic password validation on subsequent requests; adding JWT or token
revocation is outside F12.
