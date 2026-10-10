# ChairX P17 — Business RBAC and endpoint authorization

## Architecture and security boundary

`HTTP Basic → CompositeUserDetailsService → ChairxUserDetailsService → security_role_permissions → Spring Security HTTP matcher → controller → application service → repository/PostgreSQL`.

- Employees are stored in `app_users`. System roles `ADMIN`, `MANAGER`, `EMPLOYEE` and any custom roles remain in `security_roles`; assignments are stored in `security_user_roles`. Authorities come from actual database permission grants, **not Java `hasRole` hardcoding**.
- Existing `SecurityConfig` uses one `SecurityFilterChain`. Every known HTTP method+route has an explicit authorization rule; everything else ends in `anyRequest().denyAll()`. Error dispatcher is permitted to preserve controlled JSON error handling.
- Technical `catalog` credentials only provide `CATALOG_ACCESS`: **read products, variants, their lists**. Catalog user cannot modify any resource or read customers, sales, staff or money. A valid CSRF token never grants permission.
- Spring HTTP Basic, 401 `AUTHENTICATION_REQUIRED`, 403 `ACCESS_DENIED`, GET `/api/csrf` and existing CSRF verification are retained. No JWT, OAuth2, Keycloak, new IAM database, wild-card permissive authorization or global CSRF bypass.
- Access decisions restrict *whether* a caller may act. The existing service checks *whether* the operation is valid (balances, delivery states, stock, return quantities, closing), and PostgreSQL enforces transactional integrity.

## Endpoint inventory and permission matrix

Source inspection of the P20 branch found **24 REST controllers, 102 HTTP endpoints**, including `GET /api/csrf`. All endpoints below have explicit method-specific rules.

| Method | Route(s) | Required permission |
|---|---|---|
| GET | `/api/csrf` | Authenticated (including catalog user) |
| GET | `/api/products`, `/api/products/{id}`, `/api/products/{productId}/variants`, `/api/product-variants/{id}` | `CATALOG_READ` **or** `CATALOG_ACCESS` |
| POST | `/api/products`, `/api/products/{id}/activate`, `/api/products/{id}/deactivate`, `/api/products/{productId}/variants`, `/api/product-variants/{id}/activate`, `/api/product-variants/{id}/deactivate` | `CATALOG_MANAGE` |
| PUT | `/api/products/{id}`, `/api/product-variants/{id}` | `CATALOG_MANAGE` |
| GET | `/api/suppliers`, `/api/suppliers/{id}` | `SUPPLIERS_READ` |
| POST | `/api/suppliers`, `/api/suppliers/{id}/activate`, `/api/suppliers/{id}/deactivate` | `SUPPLIERS_MANAGE` |
| PUT | `/api/suppliers/{id}` | `SUPPLIERS_MANAGE` |
| GET | `/api/customers`, `/api/customers/{id}`, `/api/customers/search` | `CUSTOMERS_READ` |
| POST | `/api/customers` | `CUSTOMERS_CREATE` |
| PUT | `/api/customers/{id}` | `CUSTOMERS_MANAGE` |
| POST | `/api/customers/{id}/activate`, `/api/customers/{id}/deactivate` | `CUSTOMERS_MANAGE` |
| GET | `/api/warehouses`, `/api/warehouses/{id}` | `INVENTORY_READ` |
| POST | `/api/warehouses`, `/api/warehouses/{id}/activate`, `/api/warehouses/{id}/deactivate` | `WAREHOUSES_MANAGE` |
| PUT | `/api/warehouses/{id}` | `WAREHOUSES_MANAGE` |
| GET | `/api/purchases`, `/api/purchases/{id}`, `/api/purchases/{id}/receipts`, `/api/purchases/{id}/receipts/{receiptId}` | `PURCHASE_READ` |
| POST | `/api/purchases` | `PURCHASE_CREATE` |
| PUT | `/api/purchases/{id}`, `/api/purchases/{id}/cargo` | `PURCHASE_UPDATE` |
| POST | `/api/purchases/{id}/confirm` | `PURCHASE_CONFIRM` |
| POST | `/api/purchases/{id}/cancel` | `PURCHASE_CANCEL` |
| POST | `/api/purchases/{id}/receipts` | `INVENTORY_RECEIVE` |
| GET | `/api/purchases/{purchaseId}/payments` | `PURCHASE_PAYMENTS_READ` |
| POST | `/api/purchases/{purchaseId}/payments` | **`PURCHASE_PAYMENTS_CREATE`** |
| GET | `/api/inventory/balances`, `/api/inventory/balances/{warehouseId}/{variantId}`, `/api/inventory/movements`, `/api/inventory/transfers`, `/api/inventory/transfers/{id}` | `INVENTORY_READ` |
| POST | `/api/inventory/transfers` | `INVENTORY_TRANSFER` |
| GET | `/api/sales`, `/api/sales/{id}` | `SALES_READ` |
| POST | `/api/sales` | `SALES_CREATE` |
| POST | `/api/sales/{id}/fulfill` | `SALES_UPDATE` |
| POST | `/api/sales/{id}/cancel` | **`SALES_CANCEL`** |
| GET | `/api/deliveries`, `/api/deliveries/{id}` | `DELIVERIES_READ` |
| POST | `/api/deliveries`, `/api/deliveries/{id}/dispatch`, `/api/deliveries/{id}/deliver`, `/api/deliveries/{id}/fail`, `/api/deliveries/{id}/return-to-warehouse` | `DELIVERIES_MANAGE` |
| POST | `/api/deliveries/{id}/cancel` | **`SALES_CANCEL`** (cancels linked sale; no delivery-manager bypass) |
| GET | `/api/returns`, `/api/returns/{id}` | `RETURNS_READ` |
| POST | `/api/returns` | `RETURNS_CREATE` |
| GET | `/api/payments/{id}`, `/api/payments/sale/{saleId}`, `/api/payments/sale/{saleId}/active` | `PAYMENTS_READ` |
| POST | `/api/payments` | `PAYMENTS_CREATE` |
| POST | `/api/payments/{id}/cancel` | **`PAYMENTS_CANCEL`** |
| GET | `/api/refunds/{id}`, `/api/refunds/sale/{saleId}` | `REFUNDS_READ` |
| POST | `/api/refunds` | **`REFUNDS_CREATE`** |
| GET | `/api/exchanges/{id}`, `/api/exchanges/{id}/settlements` | `EXCHANGES_READ` |
| POST | `/api/exchanges` | `EXCHANGES_CREATE` |
| POST | `/api/exchanges/{id}/settlements` | **`EXCHANGES_SETTLE`** |
| GET | `/api/expenses`, `/api/expenses/{id}`, `/api/expenses/total` | `EXPENSES_READ` |
| POST | `/api/expenses` | **`EXPENSES_CREATE`** |
| GET | `/api/defects`, `/api/defects/{id}` | `DEFECTS_READ` |
| POST | `/api/defects`, `/api/defects/{id}/wait-for-parts`, `/api/defects/{id}/resolve` | `DEFECTS_MANAGE` |
| POST | `/api/defects/{id}/write-off` | **`DEFECTS_WRITE_OFF`** |
| GET | `/api/finance/accounts`, `/api/finance/cash-flow`, `/api/finance/closings`, `/api/finance/closings/{date}` | `FINANCE_READ` |
| POST | `/api/finance/opening-balances` | **`FINANCE_INITIALIZE`** |
| POST | `/api/finance/transfers` | **`FINANCE_TRANSFER`** |
| POST | `/api/finance/closings/{date}` | **`FINANCE_CLOSE`** |
| GET | `/api/admin/users`, `/api/admin/users/{id}` | `USERS_READ` |
| POST | `/api/admin/users` | `USERS_CREATE` |
| PATCH | `/api/admin/users/{id}/deactivate` | `USERS_DEACTIVATE` |
| GET | `/api/admin/roles` | `ROLES_READ` |
| POST | `/api/admin/users/{id}/roles` | `ROLES_ASSIGN` |
| DELETE | `/api/admin/users/{id}/roles/{roleId}` | `ROLES_ASSIGN` |
| All others | Unknown route, method, HEAD/OPTIONS bypass or unsupported business method | **denyAll** |

Endpoint matrix is also tested by real HTTP requests in `BusinessAuthorizationIntegrationTests`. Sub-paths are matched by method and segment (single `*`, not a permissive catch-all).

## Permissions: V29/V33 + V40

**Reuse from V29:** `SALES_READ`, `SALES_CREATE`, `SALES_UPDATE`, `SALES_CANCEL`, `INVENTORY_READ`, `INVENTORY_RECEIVE`, `INVENTORY_TRANSFER`, `PURCHASE_READ`, `PURCHASE_CREATE`, `PURCHASE_CONFIRM`, `FINANCE_READ`, `FINANCE_TRANSFER`, `FINANCE_INITIALIZE`, `USERS_READ`, `USERS_CREATE`, `USERS_UPDATE`, `USERS_DEACTIVATE`, `ROLES_READ`, `ROLES_CREATE`, `ROLES_UPDATE`, `ROLES_ASSIGN`. From V30 `ADMIN_ACCESS`; from V33 `FINANCE_CLOSE`.

**Added in V40 (29):** `CATALOG_READ`, `CATALOG_MANAGE`, `SUPPLIERS_READ`, `SUPPLIERS_MANAGE`, `CUSTOMERS_READ`, `CUSTOMERS_CREATE`, `CUSTOMERS_MANAGE`, `WAREHOUSES_MANAGE`, `PURCHASE_UPDATE`, `PURCHASE_CANCEL`, `PURCHASE_PAYMENTS_READ`, `PURCHASE_PAYMENTS_CREATE`, `DELIVERIES_READ`, `DELIVERIES_MANAGE`, `RETURNS_READ`, `RETURNS_CREATE`, `PAYMENTS_READ`, `PAYMENTS_CREATE`, `PAYMENTS_CANCEL`, `REFUNDS_READ`, `REFUNDS_CREATE`, `EXCHANGES_READ`, `EXCHANGES_CREATE`, `EXCHANGES_SETTLE`, `EXPENSES_READ`, `EXPENSES_CREATE`, `DEFECTS_READ`, `DEFECTS_MANAGE`, `DEFECTS_WRITE_OFF`.

## Default system-role matrix

The actual permissions are granted via `security_role_permissions` (customizations may exist). V40 **adds** grants without removing any existing grants, roles or users:

| Ability | ADMIN | MANAGER | EMPLOYEE | catalog |
|---|---|---|---|---|
| Catalog read | ✅ | ✅ | ✅ | ✅ |
| Catalog edit | ✅ | ✅ | ❌ | ❌ |
| Customer read / create | ✅ | ✅ | ✅ | ❌ |
| Supplier management | ✅ | ✅ | ❌ | ❌ |
| Purchase create/confirm/receive | ✅ | ✅ | ❌ | ❌ |
| Supplier/cargo payment | ✅ | ❌ | ❌ | ❌ |
| Inventory read | ✅ | ✅ | ✅ | ❌ |
| Inventory transfer | ✅ | ✅ | ❌ | ❌ |
| Sales read/create | ✅ | ✅ | ✅ | ❌ |
| Sales fulfill | ✅ | ✅ | ❌ | ❌ |
| Sale or linked delivery cancellation | ✅ | ❌ (no SALES_CANCEL) | ❌ | ❌ |
| Delivery read | ✅ | ✅ | ✅ | ❌ |
| Delivery dispatch/manage | ✅ | ✅ | ❌ | ❌ |
| Customer physical return | ✅ | ✅ | ❌ | ❌ |
| Sales payment registration | ✅ | ✅ | ❌ | ❌ |
| Cancel registered payment | ✅ | ❌ | ❌ | ❌ |
| Customer money refund | ✅ | ❌ | ❌ | ❌ |
| Exchange create/read | ✅ | ✅ | ❌ | ❌ |
| Exchange financial settlement | ✅ | ❌ | ❌ | ❌ |
| View operating expenses | ✅ | ✅ | ❌ | ❌ |
| Create operating expense | ✅ | ❌ | ❌ | ❌ |
| Defect manage | ✅ | ✅ | ❌ | ❌ |
| Defect write-off | ✅ | ❌ | ❌ | ❌ |
| Finance read | ✅ | ✅ | ❌ | ❌ |
| Finance transfer/initialize/close | ✅ | ❌ | ❌ | ❌ |
| Manage employees and permissions | ✅ | ❌ | ❌ | ❌ |

An administrator still cannot bypass application invariants (funds, stock, closing day, returns). A custom role may have explicitly assigned grants and must be audited before production use.

## Migration / upgrade policy

`V40__complete_business_rbac.sql` inserts only missing permissions, grants newly introduced permissions to ADMIN, selected operating permissions to MANAGER and a limited reading/customer-create set to EMPLOYEE. It additionally grants existing `PURCHASE_CONFIRM` to MANAGER. It **does not** delete accounts, role assignments, previously granted permissions, passwords, security audit logs or business records. All inserts obey existing primary/unique keys and `ON CONFLICT DO NOTHING`.

Test both clean PostgreSQL 17 and upgrade V39→V40 preserving a custom role and its role grants, password hash and security audit entries. Do not edit V1–V39.

## Test and verification plan

- `BusinessAuthorizationIntegrationTests`: 401/403 responses, catalog limitations, deny-by-default HEAD/OPTIONS/DELETE, sensitive cross-controller bypass paths, denied operations leaving balances, journal, sale/return and stock counts unchanged; CSRF required even with permission.
- `BusinessRolePermissionsIntegrationTests`: real employee accounts/roles in PostgreSQL, real HTTP Basic, changes to role assignments reflected on new authentication, invalid/disabled accounts and default role matrix.
- `BusinessRbacMigrationIntegrationTests`: fresh schema + preserved legacy data on V39 upgrade.
- Existing security tests still verify last-active-admin invariants, bootstrap and administrative functionality. Existing business-API tests now provide exactly the authorizations required for their domain operations; tests asserting only authentication access with technical catalog user have been corrected.
- Run `cd backend && mvn clean verify` with Java 25 and Testcontainers/PostgreSQL 17. Until this passes, verification level is **source inspection only**. Do not merge the draft PR or claim the package is production-ready.

When creating a new business endpoint, add its explicit HTTP method+URL matcher **before denyAll**, map it to a seeded permission and system-role policy, add a negative/no-side-effects test, and update this document. Never loosen the default matcher to `authenticated()` or disable CSRF to make a test pass.


## P23 extension — active ADMIN, mutable role grants and read models

See [P23 role management](p23-role-management.md). P23 is dependent on PR #14 and
PR #15. It adds five secured admin endpoints; an active PostgreSQL system ADMIN
check is required **in addition to** the original `ROLES_*` and `USERS_*`
permissions. The user and role tables are reused. Role edits are versioned,
transactional and audited. Default `EMPLOYEE` receives `SALES_DRAFT_MANAGE`
but not `SALES_UPDATE`; existing MANAGER `FINANCE_READ` is preserved.
`GET /api/purchases/receiving` and
`GET /api/purchases/{id}/receiving-summary` require `INVENTORY_RECEIVE`
without granting `PURCHASE_READ` or exposing procurement prices.
