# ChairX F02 — Authentication & Security

## Scope and architecture

F02 adds a **real staff login** to the existing F01 React shell. It does not
add registration, password-reset flows, a new session system or a new user table.

Authentication pipeline:
1. Employee enters username and password at /login.
2. Frontend encodes both values as UTF-8 HTTP Basic and calls same-origin
   GET /api/auth/me.
3. Spring Security verifies against app_users.password_hash and active status.
4. CurrentUserController re-reads employee identity, roles and effective
   permissions from PostgreSQL.
5. Frontend GETs /api/csrf with the same Basic header and existing session cookie.
6. Only after both calls succeed, AuthProvider exposes the profile and allowed menu.
7. All later business requests use the existing centralized ApiClient with
   memory-only Authorization and the server-provided CSRF header on unsafe methods.

`GET /api/auth/me` example:
~~~json
{
  "id": "00000000-0000-0000-0000-000000000010",
  "username": "employee",
  "displayName": "Сотрудник",
  "roles": ["EMPLOYEE"],
  "permissions": ["CATALOG_READ", "INVENTORY_READ", "SALES_READ"]
}
~~~

The response is built from real app_users, security_user_roles and
security_role_permissions. No password, password hash or secret is returned.
HTTP 401: absent/incorrect Basic credentials or disabled account.
HTTP 403: catalog technical account, staff account without a role, or operation
forbidden by permissions. Unknown routes remain denyAll.

## Password and CSRF rules

Passwords/Basic values and tokens exist **only in JavaScript memory** for the
active tab; never in localStorage, sessionStorage, IndexedDB, URL or analytics.
Refreshing the page requires logging in again. No remember-me.

Frontend API calls are same-origin. Vite's /api proxy supplies access in dev;
the browser does not send Basic headers to a separate API host.
Mutating POST/PUT/PATCH/DELETE requests require the headerName and token
returned by /api/csrf. If missing, the frontend aborts *before* making an
unsafe request. The backend remains responsible for real CSRF validation.
403 does not trigger logout or automatic mutation retries; 401 clears memory.

Logout clears in-memory credentials, CSRF, staff profile, query cache and
cancels active tracked business requests. **HTTP Basic has no reliable
server-side session logout**: a browser may retain its own Basic credential
cache. F02 cannot promise remote revocation. Avoid public terminals, use HTTPS
for all non-local traffic, and close the tab when finished.

## Authorization and menus

The backend is the **source of truth**: frontend checks improve UX, they do
not replace backend permission enforcement. Each sidebar item and deep link
requires the matching read permission. /daily-closing also accepts FINANCE_READ
(the real backend route allows it); /admin accepts USERS_READ or ROLES_READ.
Home is available to any authenticated employee. Sections still display the
F01 honest placeholders: F02 adds authentication only, not new ERP operations.

## Testing

- Backend integration test: CurrentUserApiIntegrationTest checks actual
  ADMIN/MANAGER/EMPLOYEE grants from PostgreSQL Testcontainers, bad password,
  missing credentials, disabled staff, roleless staff, catalog exclusion,
  data minimization, CSRF and unknown route policy.
- Frontend: React Testing Library + Vitest verify login, errors, 403,
  protected deep links, permissions, logout, cache clearing, CSRF,
  safe same-origin headers, network failure and role changes.
- CI: backend-verify.yml, frontend verification, local-dev-smoke.yml.

## Production risks / limitations

- **Basic credentials are repeated on each authenticated request**, so HTTPS,
  reverse proxy security headers, safe cookie flags, request log redaction,
  credentials protection and rate limiting must be reviewed before deployment.
- Logout has no server-side token/session invalidation or immediate revocation.
  Disabled users fail the *next* server authentication; frontend menu data is
  a snapshot at login and does not continuously refetch permission changes.
- Unauthenticated page refresh intentionally returns to /login.
- Browser-managed Basic credential caching varies; test actual target
  browsers in deployment, especially logout/401 challenge behavior.
- CSRF sessions are managed by the existing Spring Security configuration,
  not a new auth scheme. A server restart/session expiration can require
  logging in again.
- Local dev accounts from F01.1 must never be enabled in production.


## Native browser Basic prompt on invalid credentials

The JSON API deliberately omits the `WWW-Authenticate: Basic` challenge
for HTTP 401 responses on `/api/*`. Without this, browsers may open their
native Basic password dialog over the ChairX login page whenever a user
enters a wrong password. JSON errors and status 401 are preserved; HTTP Basic
credential verification remains unchanged. Non-API responses may still use
the normal HTTP Basic challenge.
