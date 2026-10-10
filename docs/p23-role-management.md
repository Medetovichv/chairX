# P23 — Roles, permissions and secure operational read models

## Dependency / release order

P23 was built from P22 branch `feat/p22-operational-workflows` (PR #15), because
`main` was missing P22 as well as F02 (PR #14) at branch creation.
The backend F02 `GET /api/auth/me` endpoint and browser-safe HTTP Basic 401 are included.
Do not merge this P23 draft into main without reconciling both open PRs and re-running CI.

## Security model

Employees authenticate through existing HTTP Basic and PostgreSQL `app_users`.
No JWT, external IAM, direct employee grants, role inheritance or new auth tables.
Permissions are the **union** of permissions of assigned roles, reloaded through
`ChairxUserDetailsService` on a fresh HTTP Basic authentication. The current
role and permission sets are visible at `GET /api/auth/me`.

`ADMIN`, `MANAGER`, `EMPLOYEE` keep their stable database identifiers.
Additional role codes, for example `WAREHOUSE`, `COURIER`, `ACCOUNTANT`,
are created by an administrator; they are not seeded automatically.
Only an **active** employee assigned the real `ADMIN` system role in PostgreSQL
may call admin endpoints or use role/user administration services, in addition
to holding the required permission. Possessing a custom role called "admin"
or a copied administrative permission is never sufficient.

Administrative grants `ADMIN_ACCESS`, `ROLES_*`, `USERS_*` and
`DAILY_CLOSING_UNLOCK_ADMIN` are prohibited for non-ADMIN role edits.
The technical `CATALOG_ACCESS` account is outside the database employee role
system and is not exposed by the permissions API. ADMIN cannot be renamed,
edited, removed or downgraded. The existing serialized last-active-ADMIN
protection for account deactivation and role removal remains in place.

Every role update uses `version` and `expectedVersion` for optimistic
concurrency and the existing PostgreSQL transaction advisory lock to serialize
role edits with assignments. Stale edits return HTTP 409. Role creation and role
updates atomically apply grant changes and audit events in `security_audit_log`.
`ROLE_CREATED`, `ROLE_UPDATED` and `ROLE_PERMISSIONS_CHANGED` retain
the actor, target role, before/after grant lists and reason. Assignment events
remain `ROLE_ASSIGNED` and `ROLE_REMOVED`.

## API

All requests require an authenticated active PostgreSQL ADMIN with the
matching role-management permission. Mutating requests require CSRF.

| Method | Path | Permission |
|---|---|---|
| GET | `/api/admin/roles` | ROLES_READ + real ADMIN |
| GET | `/api/admin/roles/{id}` | ROLES_READ + real ADMIN |
| POST | `/api/admin/roles` | ROLES_CREATE + real ADMIN |
| PUT | `/api/admin/roles/{id}` | ROLES_UPDATE + real ADMIN |
| GET | `/api/admin/permissions` | ROLES_READ + real ADMIN |
| POST | `/api/admin/users/{id}/roles` | ROLES_ASSIGN + real ADMIN |
| DELETE | `/api/admin/users/{id}/roles/{roleId}` | ROLES_ASSIGN + real ADMIN |

Create custom role:

```json
{"code":"WAREHOUSE","name":"Кладовщик",
 "permissions":["CATALOG_READ","INVENTORY_READ","INVENTORY_RECEIVE"]}
```

Change role:

```json
{"name":"Кладовщик","permissions":["CATALOG_READ","INVENTORY_READ"],
 "expectedVersion":0,"reason":"Распределение обязанностей"}
```

Response: `id`, `code`, `name`, `systemRole`, `permissions`,
`assignedUsersCount`, `version`. Existing list consumers that use
`id`, `code`, `name` continue to receive those attributes. Permission
catalog returns `code`, `description`, `group`, `sensitive`,
`adminOnly`; grouping is UI metadata, not new grants.

## Default permissions

V51 introduces only `SALES_DRAFT_MANAGE` and seeds it for
ADMIN, MANAGER and EMPLOYEE. Employees may edit and confirm sales drafts but
`POST /api/sales/{id}/fulfill` stays protected by `SALES_UPDATE`.
`SALES_CREATE` still guards creation of a draft. All other existing
role permissions remain unchanged, including MANAGER `FINANCE_READ`.
Custom roles have no grants unless an administrator explicitly selects them.

`ADMIN` receives all current entries in the real `security_permissions` table
at migration time. For future new permission migrations, seed ADMIN in that
same migration.

## Permission-aware read models

Related objects do not imply inherited permission grants. The P22 sale list
(`SALES_READ`) intentionally contains only order, customer display,
fulfillment and payment-status summaries; it does not contain company ledgers,
vendor purchase costs, cash-flow, expenses or supplier payments. Full customer
details require `CUSTOMERS_READ`; full payments require `PAYMENTS_READ`.

P23 adds warehouse-only `GET /api/purchases/receiving` (paginated worklist)
and `GET /api/purchases/{id}/receiving-summary`. Both require only
`INVENTORY_RECEIVE`, and their DTOs contain purchase/item IDs, model/variant,
ordered, received and remaining quantities, creation time and purchase status,
but **no** unit price, supplier payment, cargo or financial journal.
The purchase management endpoints still require `PURCHASE_READ`.

P21/P22 Daily Closing operational endpoints accept `DAILY_CLOSING_READ`
without `FINANCE_READ`. Cash/bank expected/actual figures are scoped
to day closing. They do not confer general finance, expense or supplier-payment
read permissions. P21 transaction logic and P22 immutable completion snapshots
remain unchanged.

## Database changes and tests

`V51__p23_roles_and_sales_drafts.sql` adds `security_roles.version` with
nonnegative constraint, the sole new permission and grants. It does not create
new auth tables, modify existing UUIDs, or rewrite earlier Flyway migrations.
Tests are in `P23RoleManagementIntegrationTests` and the F02
`CurrentUserApiIntegrationTest`, using HTTP Basic and PostgreSQL Testcontainers.

## MVP limitations / release gates

- Role permissions are global by role; there is no per-field ACL.
- Role membership and permissions are re-evaluated for **fresh Basic-authenticated**
  requests; no JWT or frontend-only authorization is trusted.
- A client must refresh `/api/auth/me` after an administrator changes its role.
- Migrate a copy of pre-P23 production data before deploying V51.
- Integrate F02 and P22 before promoting P23; run full Maven verification,
  GitHub Actions, and frontend response-contract checks after integration.

### Read security audit

`GET /api/admin/audit?page=0&size=20&targetId=<uuid>` returns a paginated
security audit history, including `actorUserId`, `action`, `targetType`,
`targetId`, `details` and `createdAt`. The `targetId` filter is optional.
Only the real system ADMIN with `ROLES_READ` may access this endpoint.
