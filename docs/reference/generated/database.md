<!-- Auto-generated; do not edit. See scripts/generate-codebase-reference.py -->

# Database data dictionary

Heuristic reconstruction from Flyway SQL, not a live DB introspection. Inspect migrations for renames, dynamic statements, complex constraints and exact foreign keys. Never treat this index as authorization for data migrations.

Migration files: 73. Detected tables: 55.

## Foreign-key references detected

| Table | Target | Source |
|---|---|---|
| customer_addresses | customers(id) | [backend/src/main/resources/db/migration/V1003__customer_additional_addresses.sql](../../../backend/src/main/resources/db/migration/V1003__customer_additional_addresses.sql) |
| defects | warehouses(id) | [backend/src/main/resources/db/migration/V5__create_defects.sql](../../../backend/src/main/resources/db/migration/V5__create_defects.sql) |
| defects | product_variants(id) | [backend/src/main/resources/db/migration/V5__create_defects.sql](../../../backend/src/main/resources/db/migration/V5__create_defects.sql) |
| defects | suppliers(id) | [backend/src/main/resources/db/migration/V5__create_defects.sql](../../../backend/src/main/resources/db/migration/V5__create_defects.sql) |
| defects | purchase_receipt_items(id) | [backend/src/main/resources/db/migration/V5__create_defects.sql](../../../backend/src/main/resources/db/migration/V5__create_defects.sql) |
| deliveries | sales(id) | [backend/src/main/resources/db/migration/V11__create_deliveries.sql](../../../backend/src/main/resources/db/migration/V11__create_deliveries.sql) |
| deliveries | warehouses(id) | [backend/src/main/resources/db/migration/V11__create_deliveries.sql](../../../backend/src/main/resources/db/migration/V11__create_deliveries.sql) |
| employee_daily_closing_expenses | employee_daily_closings(id) | [backend/src/main/resources/db/migration/V58__employee_daily_closing_expenses.sql](../../../backend/src/main/resources/db/migration/V58__employee_daily_closing_expenses.sql) |
| employee_daily_closing_sales | employee_daily_closings(id) | [backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql](../../../backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql) |
| employee_daily_closings | app_users(id) | [backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql](../../../backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql) |
| expense_accrual_payments | expense_accruals(id) | [backend/src/main/resources/db/migration/V1001__expense_accrual_payments.sql](../../../backend/src/main/resources/db/migration/V1001__expense_accrual_payments.sql) |
| expense_accrual_payments | expenses(id) | [backend/src/main/resources/db/migration/V1001__expense_accrual_payments.sql](../../../backend/src/main/resources/db/migration/V1001__expense_accrual_payments.sql) |
| finance_admin_adjustments | finance_accounts(code) | [backend/src/main/resources/db/migration/V56__finance_admin_adjustments.sql](../../../backend/src/main/resources/db/migration/V56__finance_admin_adjustments.sql) |
| finance_daily_closing_accounts | finance_daily_closings(id) | [backend/src/main/resources/db/migration/V32__daily_finance_closing.sql](../../../backend/src/main/resources/db/migration/V32__daily_finance_closing.sql) |
| finance_daily_closing_accounts | finance_accounts(code) | [backend/src/main/resources/db/migration/V32__daily_finance_closing.sql](../../../backend/src/main/resources/db/migration/V32__daily_finance_closing.sql) |
| finance_daily_closing_adjustments | finance_daily_closing_accounts(closing_id, account_code) | [backend/src/main/resources/db/migration/V45__daily_closing_recalculated_balances.sql](../../../backend/src/main/resources/db/migration/V45__daily_closing_recalculated_balances.sql) |
| finance_daily_closing_sales | finance_daily_closing_sales_snapshots(closing_id) | [backend/src/main/resources/db/migration/V50__p22_daily_closing_sales_snapshot.sql](../../../backend/src/main/resources/db/migration/V50__p22_daily_closing_sales_snapshot.sql) |
| finance_daily_closing_sales_snapshots | finance_daily_closings(id) | [backend/src/main/resources/db/migration/V50__p22_daily_closing_sales_snapshot.sql](../../../backend/src/main/resources/db/migration/V50__p22_daily_closing_sales_snapshot.sql) |
| finance_daily_closing_unlocks | finance_daily_closing_access_locks(business_date) | [backend/src/main/resources/db/migration/V41__daily_closing_unlocks.sql](../../../backend/src/main/resources/db/migration/V41__daily_closing_unlocks.sql) |
| finance_movements | finance_accounts(code) | [backend/src/main/resources/db/migration/V24__create_finance_accounts.sql](../../../backend/src/main/resources/db/migration/V24__create_finance_accounts.sql) |
| finance_transfers | finance_accounts(code) | [backend/src/main/resources/db/migration/V27__create_finance_transfers.sql](../../../backend/src/main/resources/db/migration/V27__create_finance_transfers.sql) |
| finance_transfers | finance_accounts(code) | [backend/src/main/resources/db/migration/V27__create_finance_transfers.sql](../../../backend/src/main/resources/db/migration/V27__create_finance_transfers.sql) |
| inventory_balances | warehouses(id) | [backend/src/main/resources/db/migration/V3__create_inventory.sql](../../../backend/src/main/resources/db/migration/V3__create_inventory.sql) |
| inventory_balances | product_variants(id) | [backend/src/main/resources/db/migration/V3__create_inventory.sql](../../../backend/src/main/resources/db/migration/V3__create_inventory.sql) |
| inventory_cost_allocations | stock_movements(id) | [backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql](../../../backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql) |
| inventory_cost_allocations | inventory_cost_layers(id) | [backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql](../../../backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql) |
| inventory_cost_layers | warehouses(id) | [backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql](../../../backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql) |
| inventory_cost_layers | product_variants(id) | [backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql](../../../backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql) |
| inventory_cost_layers | stock_movements(id) | [backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql](../../../backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql) |
| inventory_cost_movements | inventory_cost_layers(id) | [backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql](../../../backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql) |
| inventory_cost_movements | stock_movements(id) | [backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql](../../../backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql) |
| inventory_cost_restorations | stock_movements(id) | [backend/src/main/resources/db/migration/V22__restore_and_protect_inventory_cost.sql](../../../backend/src/main/resources/db/migration/V22__restore_and_protect_inventory_cost.sql) |
| inventory_cost_restorations | inventory_cost_allocations(id) | [backend/src/main/resources/db/migration/V22__restore_and_protect_inventory_cost.sql](../../../backend/src/main/resources/db/migration/V22__restore_and_protect_inventory_cost.sql) |
| inventory_cost_write_offs | stock_movements(id) | [backend/src/main/resources/db/migration/V22__restore_and_protect_inventory_cost.sql](../../../backend/src/main/resources/db/migration/V22__restore_and_protect_inventory_cost.sql) |
| inventory_cost_write_offs | purchase_receipt_items(id) | [backend/src/main/resources/db/migration/V22__restore_and_protect_inventory_cost.sql](../../../backend/src/main/resources/db/migration/V22__restore_and_protect_inventory_cost.sql) |
| inventory_transfer_cost_origins | inventory_transfers(id) | [backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql](../../../backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql) |
| inventory_transfer_cost_origins | inventory_cost_allocations(id) | [backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql](../../../backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql) |
| inventory_transfers | warehouses(id) | [backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql](../../../backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql) |
| inventory_transfers | warehouses(id) | [backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql](../../../backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql) |
| inventory_transfers | product_variants(id) | [backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql](../../../backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql) |
| inventory_transfers | stock_movements(id) | [backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql](../../../backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql) |
| inventory_transfers | stock_movements(id) | [backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql](../../../backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql) |
| payments | sales(id) | [backend/src/main/resources/db/migration/V12__create_payments.sql](../../../backend/src/main/resources/db/migration/V12__create_payments.sql) |
| product_variants | products(id) | [backend/src/main/resources/db/migration/V1__create_product_catalog.sql](../../../backend/src/main/resources/db/migration/V1__create_product_catalog.sql) |
| purchase_items | purchases(id) | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| purchase_items | product_variants(id) | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| purchase_payments | purchases(id) | [backend/src/main/resources/db/migration/V37__purchase_payments.sql](../../../backend/src/main/resources/db/migration/V37__purchase_payments.sql) |
| purchase_payments | finance_accounts(code) | [backend/src/main/resources/db/migration/V37__purchase_payments.sql](../../../backend/src/main/resources/db/migration/V37__purchase_payments.sql) |
| purchase_receipt_items | purchase_receipts(purchase_id, id) | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| purchase_receipt_items | purchase_items(purchase_id, id) | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| purchase_receipts | purchases(id) | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| purchase_receipts | warehouses(id) | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| purchases | suppliers(id) | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| refunds | sales(id) | [backend/src/main/resources/db/migration/V15__create_refunds.sql](../../../backend/src/main/resources/db/migration/V15__create_refunds.sql) |
| refunds | returns(id) | [backend/src/main/resources/db/migration/V15__create_refunds.sql](../../../backend/src/main/resources/db/migration/V15__create_refunds.sql) |
| return_items | returns(id) | [backend/src/main/resources/db/migration/V13__create_returns.sql](../../../backend/src/main/resources/db/migration/V13__create_returns.sql) |
| return_items | sale_items(id) | [backend/src/main/resources/db/migration/V13__create_returns.sql](../../../backend/src/main/resources/db/migration/V13__create_returns.sql) |
| returns | sales(id) | [backend/src/main/resources/db/migration/V13__create_returns.sql](../../../backend/src/main/resources/db/migration/V13__create_returns.sql) |
| returns | warehouses(id) | [backend/src/main/resources/db/migration/V13__create_returns.sql](../../../backend/src/main/resources/db/migration/V13__create_returns.sql) |
| sale_comments | sales(id) | [backend/src/main/resources/db/migration/V55__sale_employee_comments.sql](../../../backend/src/main/resources/db/migration/V55__sale_employee_comments.sql) |
| sale_items | sales(id) | [backend/src/main/resources/db/migration/V8__create_sales.sql](../../../backend/src/main/resources/db/migration/V8__create_sales.sql) |
| sale_items | product_variants(id) | [backend/src/main/resources/db/migration/V8__create_sales.sql](../../../backend/src/main/resources/db/migration/V8__create_sales.sql) |
| sale_items | warehouses(id) | [backend/src/main/resources/db/migration/V8__create_sales.sql](../../../backend/src/main/resources/db/migration/V8__create_sales.sql) |
| sales | customers(id) | [backend/src/main/resources/db/migration/V8__create_sales.sql](../../../backend/src/main/resources/db/migration/V8__create_sales.sql) |
| security_audit_log | app_users(id) | [backend/src/main/resources/db/migration/V28__create_security_rbac.sql](../../../backend/src/main/resources/db/migration/V28__create_security_rbac.sql) |
| security_role_permissions | security_roles(id) | [backend/src/main/resources/db/migration/V28__create_security_rbac.sql](../../../backend/src/main/resources/db/migration/V28__create_security_rbac.sql) |
| security_role_permissions | security_permissions(code) | [backend/src/main/resources/db/migration/V28__create_security_rbac.sql](../../../backend/src/main/resources/db/migration/V28__create_security_rbac.sql) |
| security_user_roles | app_users(id) | [backend/src/main/resources/db/migration/V28__create_security_rbac.sql](../../../backend/src/main/resources/db/migration/V28__create_security_rbac.sql) |
| security_user_roles | security_roles(id) | [backend/src/main/resources/db/migration/V28__create_security_rbac.sql](../../../backend/src/main/resources/db/migration/V28__create_security_rbac.sql) |
| stock_movements | inventory_balances(warehouse_id, product_variant_id) | [backend/src/main/resources/db/migration/V3__create_inventory.sql](../../../backend/src/main/resources/db/migration/V3__create_inventory.sql) |

## Columns

### app_users

| Column | Last recognized declaration | Source |
|---|---|---|
| active | BOOLEAN NOT NULL DEFAULT TRUE | [backend/src/main/resources/db/migration/V28__create_security_rbac.sql](../../../backend/src/main/resources/db/migration/V28__create_security_rbac.sql) |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT NOW() | [backend/src/main/resources/db/migration/V28__create_security_rbac.sql](../../../backend/src/main/resources/db/migration/V28__create_security_rbac.sql) |
| display_name | VARCHAR(200) NOT NULL | [backend/src/main/resources/db/migration/V28__create_security_rbac.sql](../../../backend/src/main/resources/db/migration/V28__create_security_rbac.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V28__create_security_rbac.sql](../../../backend/src/main/resources/db/migration/V28__create_security_rbac.sql) |
| password_hash | VARCHAR(255) NOT NULL | [backend/src/main/resources/db/migration/V28__create_security_rbac.sql](../../../backend/src/main/resources/db/migration/V28__create_security_rbac.sql) |
| username | VARCHAR(100) NOT NULL UNIQUE | [backend/src/main/resources/db/migration/V28__create_security_rbac.sql](../../../backend/src/main/resources/db/migration/V28__create_security_rbac.sql) |

### audit_entries

| Column | Last recognized declaration | Source |
|---|---|---|
| action | VARCHAR(40) NOT NULL | [backend/src/main/resources/db/migration/V1__create_product_catalog.sql](../../../backend/src/main/resources/db/migration/V1__create_product_catalog.sql) |
| actor | VARCHAR(200) NOT NULL | [backend/src/main/resources/db/migration/V1__create_product_catalog.sql](../../../backend/src/main/resources/db/migration/V1__create_product_catalog.sql) |
| after_state | JSONB NOT NULL | [backend/src/main/resources/db/migration/V1__create_product_catalog.sql](../../../backend/src/main/resources/db/migration/V1__create_product_catalog.sql) |
| before_state | JSONB | [backend/src/main/resources/db/migration/V1__create_product_catalog.sql](../../../backend/src/main/resources/db/migration/V1__create_product_catalog.sql) |
| entity_id | UUID NOT NULL | [backend/src/main/resources/db/migration/V1__create_product_catalog.sql](../../../backend/src/main/resources/db/migration/V1__create_product_catalog.sql) |
| entity_type | VARCHAR(40) NOT NULL | [backend/src/main/resources/db/migration/V1__create_product_catalog.sql](../../../backend/src/main/resources/db/migration/V1__create_product_catalog.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V1__create_product_catalog.sql](../../../backend/src/main/resources/db/migration/V1__create_product_catalog.sql) |
| occurred_at | TIMESTAMPTZ NOT NULL | [backend/src/main/resources/db/migration/V1__create_product_catalog.sql](../../../backend/src/main/resources/db/migration/V1__create_product_catalog.sql) |

### customer_addresses

| Column | Last recognized declaration | Source |
|---|---|---|
| address | VARCHAR(500) NOT NULL | [backend/src/main/resources/db/migration/V1003__customer_additional_addresses.sql](../../../backend/src/main/resources/db/migration/V1003__customer_additional_addresses.sql) |
| city_region | VARCHAR(200) | [backend/src/main/resources/db/migration/V1003__customer_additional_addresses.sql](../../../backend/src/main/resources/db/migration/V1003__customer_additional_addresses.sql) |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT now() | [backend/src/main/resources/db/migration/V1003__customer_additional_addresses.sql](../../../backend/src/main/resources/db/migration/V1003__customer_additional_addresses.sql) |
| customer_id | UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE | [backend/src/main/resources/db/migration/V1003__customer_additional_addresses.sql](../../../backend/src/main/resources/db/migration/V1003__customer_additional_addresses.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V1003__customer_additional_addresses.sql](../../../backend/src/main/resources/db/migration/V1003__customer_additional_addresses.sql) |

### customers

| Column | Last recognized declaration | Source |
|---|---|---|
| active | BOOLEAN NOT NULL DEFAULT TRUE | [backend/src/main/resources/db/migration/V7__create_customers.sql](../../../backend/src/main/resources/db/migration/V7__create_customers.sql) |
| address | VARCHAR(500) | [backend/src/main/resources/db/migration/V7__create_customers.sql](../../../backend/src/main/resources/db/migration/V7__create_customers.sql) |
| city_region | VARCHAR(200) | [backend/src/main/resources/db/migration/V7__create_customers.sql](../../../backend/src/main/resources/db/migration/V7__create_customers.sql) |
| comment | VARCHAR(2000) | [backend/src/main/resources/db/migration/V7__create_customers.sql](../../../backend/src/main/resources/db/migration/V7__create_customers.sql) |
| created_at | TIMESTAMPTZ NOT NULL | [backend/src/main/resources/db/migration/V7__create_customers.sql](../../../backend/src/main/resources/db/migration/V7__create_customers.sql) |
| full_name | VARCHAR(200) NOT NULL | [backend/src/main/resources/db/migration/V7__create_customers.sql](../../../backend/src/main/resources/db/migration/V7__create_customers.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V7__create_customers.sql](../../../backend/src/main/resources/db/migration/V7__create_customers.sql) |
| instagram_username | VARCHAR(100) | [backend/src/main/resources/db/migration/V7__create_customers.sql](../../../backend/src/main/resources/db/migration/V7__create_customers.sql) |
| phone | VARCHAR(50) | [backend/src/main/resources/db/migration/V7__create_customers.sql](../../../backend/src/main/resources/db/migration/V7__create_customers.sql) |
| secondary_phone | VARCHAR(50) | [backend/src/main/resources/db/migration/V7__create_customers.sql](../../../backend/src/main/resources/db/migration/V7__create_customers.sql) |
| updated_at | TIMESTAMPTZ NOT NULL | [backend/src/main/resources/db/migration/V7__create_customers.sql](../../../backend/src/main/resources/db/migration/V7__create_customers.sql) |
| whatsapp_phone | VARCHAR(50) | [backend/src/main/resources/db/migration/V7__create_customers.sql](../../../backend/src/main/resources/db/migration/V7__create_customers.sql) |

### defects

| Column | Last recognized declaration | Source |
|---|---|---|
| created_at | TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp() | [backend/src/main/resources/db/migration/V5__create_defects.sql](../../../backend/src/main/resources/db/migration/V5__create_defects.sql) |
| created_by | VARCHAR(200) | [backend/src/main/resources/db/migration/V5__create_defects.sql](../../../backend/src/main/resources/db/migration/V5__create_defects.sql) |
| description | VARCHAR(2000) NOT NULL | [backend/src/main/resources/db/migration/V5__create_defects.sql](../../../backend/src/main/resources/db/migration/V5__create_defects.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V5__create_defects.sql](../../../backend/src/main/resources/db/migration/V5__create_defects.sql) |
| product_variant_id | UUID NOT NULL | [backend/src/main/resources/db/migration/V5__create_defects.sql](../../../backend/src/main/resources/db/migration/V5__create_defects.sql) |
| purchase_receipt_item_id | UUID | [backend/src/main/resources/db/migration/V5__create_defects.sql](../../../backend/src/main/resources/db/migration/V5__create_defects.sql) |
| quantity | BIGINT NOT NULL | [backend/src/main/resources/db/migration/V5__create_defects.sql](../../../backend/src/main/resources/db/migration/V5__create_defects.sql) |
| resolution_note | VARCHAR(2000) | [backend/src/main/resources/db/migration/V5__create_defects.sql](../../../backend/src/main/resources/db/migration/V5__create_defects.sql) |
| resolved_at | TIMESTAMPTZ | [backend/src/main/resources/db/migration/V5__create_defects.sql](../../../backend/src/main/resources/db/migration/V5__create_defects.sql) |
| resolved_by | VARCHAR(200) | [backend/src/main/resources/db/migration/V5__create_defects.sql](../../../backend/src/main/resources/db/migration/V5__create_defects.sql) |
| status | VARCHAR(30) NOT NULL | [backend/src/main/resources/db/migration/V5__create_defects.sql](../../../backend/src/main/resources/db/migration/V5__create_defects.sql) |
| supplier_id | UUID | [backend/src/main/resources/db/migration/V5__create_defects.sql](../../../backend/src/main/resources/db/migration/V5__create_defects.sql) |
| warehouse_id | UUID NOT NULL | [backend/src/main/resources/db/migration/V5__create_defects.sql](../../../backend/src/main/resources/db/migration/V5__create_defects.sql) |

### deliveries

| Column | Last recognized declaration | Source |
|---|---|---|
| address | VARCHAR(500) NOT NULL | [backend/src/main/resources/db/migration/V11__create_deliveries.sql](../../../backend/src/main/resources/db/migration/V11__create_deliveries.sql) |
| cancelled_at | TIMESTAMPTZ | [backend/src/main/resources/db/migration/V11__create_deliveries.sql](../../../backend/src/main/resources/db/migration/V11__create_deliveries.sql) |
| cancelled_by | VARCHAR(200) | [backend/src/main/resources/db/migration/V11__create_deliveries.sql](../../../backend/src/main/resources/db/migration/V11__create_deliveries.sql) |
| carrier_name | VARCHAR(200) | [backend/src/main/resources/db/migration/V11__create_deliveries.sql](../../../backend/src/main/resources/db/migration/V11__create_deliveries.sql) |
| city_region | VARCHAR(200) | [backend/src/main/resources/db/migration/V11__create_deliveries.sql](../../../backend/src/main/resources/db/migration/V11__create_deliveries.sql) |
| comment | VARCHAR(2000) | [backend/src/main/resources/db/migration/V11__create_deliveries.sql](../../../backend/src/main/resources/db/migration/V11__create_deliveries.sql) |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp() | [backend/src/main/resources/db/migration/V11__create_deliveries.sql](../../../backend/src/main/resources/db/migration/V11__create_deliveries.sql) |
| created_by | VARCHAR(200) NOT NULL | [backend/src/main/resources/db/migration/V11__create_deliveries.sql](../../../backend/src/main/resources/db/migration/V11__create_deliveries.sql) |
| delivered_at | TIMESTAMPTZ | [backend/src/main/resources/db/migration/V11__create_deliveries.sql](../../../backend/src/main/resources/db/migration/V11__create_deliveries.sql) |
| delivered_by | VARCHAR(200) | [backend/src/main/resources/db/migration/V11__create_deliveries.sql](../../../backend/src/main/resources/db/migration/V11__create_deliveries.sql) |
| delivery_cost | NUMERIC(19,2) NOT NULL DEFAULT 0 | [backend/src/main/resources/db/migration/V11__create_deliveries.sql](../../../backend/src/main/resources/db/migration/V11__create_deliveries.sql) |
| dispatched_at | TIMESTAMPTZ | [backend/src/main/resources/db/migration/V11__create_deliveries.sql](../../../backend/src/main/resources/db/migration/V11__create_deliveries.sql) |
| dispatched_by | VARCHAR(200) | [backend/src/main/resources/db/migration/V11__create_deliveries.sql](../../../backend/src/main/resources/db/migration/V11__create_deliveries.sql) |
| failed_at | TIMESTAMPTZ | [backend/src/main/resources/db/migration/V11__create_deliveries.sql](../../../backend/src/main/resources/db/migration/V11__create_deliveries.sql) |
| failed_by | VARCHAR(200) | [backend/src/main/resources/db/migration/V11__create_deliveries.sql](../../../backend/src/main/resources/db/migration/V11__create_deliveries.sql) |
| failure_reason | VARCHAR(1000) | [backend/src/main/resources/db/migration/V11__create_deliveries.sql](../../../backend/src/main/resources/db/migration/V11__create_deliveries.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V11__create_deliveries.sql](../../../backend/src/main/resources/db/migration/V11__create_deliveries.sql) |
| planned_delivery_date | DATE | [backend/src/main/resources/db/migration/V48__p22_planned_delivery_date.sql](../../../backend/src/main/resources/db/migration/V48__p22_planned_delivery_date.sql) |
| recipient_name | VARCHAR(200) NOT NULL | [backend/src/main/resources/db/migration/V11__create_deliveries.sql](../../../backend/src/main/resources/db/migration/V11__create_deliveries.sql) |
| recipient_phone | VARCHAR(50) NOT NULL | [backend/src/main/resources/db/migration/V11__create_deliveries.sql](../../../backend/src/main/resources/db/migration/V11__create_deliveries.sql) |
| return_warehouse_id | UUID | [backend/src/main/resources/db/migration/V11__create_deliveries.sql](../../../backend/src/main/resources/db/migration/V11__create_deliveries.sql) |
| returned_to_warehouse_at | TIMESTAMPTZ | [backend/src/main/resources/db/migration/V11__create_deliveries.sql](../../../backend/src/main/resources/db/migration/V11__create_deliveries.sql) |
| returned_to_warehouse_by | VARCHAR(200) | [backend/src/main/resources/db/migration/V11__create_deliveries.sql](../../../backend/src/main/resources/db/migration/V11__create_deliveries.sql) |
| sale_id | UUID NOT NULL UNIQUE | [backend/src/main/resources/db/migration/V11__create_deliveries.sql](../../../backend/src/main/resources/db/migration/V11__create_deliveries.sql) |
| status | VARCHAR(30) NOT NULL | [backend/src/main/resources/db/migration/V11__create_deliveries.sql](../../../backend/src/main/resources/db/migration/V11__create_deliveries.sql) |
| tracking_number | VARCHAR(200) | [backend/src/main/resources/db/migration/V11__create_deliveries.sql](../../../backend/src/main/resources/db/migration/V11__create_deliveries.sql) |

### employee_daily_closing_expenses

| Column | Last recognized declaration | Source |
|---|---|---|
| amount | NUMERIC(19,2) NOT NULL CHECK(amount > 0) | [backend/src/main/resources/db/migration/V58__employee_daily_closing_expenses.sql](../../../backend/src/main/resources/db/migration/V58__employee_daily_closing_expenses.sql) |
| category | VARCHAR(50) NOT NULL | [backend/src/main/resources/db/migration/V58__employee_daily_closing_expenses.sql](../../../backend/src/main/resources/db/migration/V58__employee_daily_closing_expenses.sql) |
| closing_id | UUID NOT NULL REFERENCES employee_daily_closings(id) | [backend/src/main/resources/db/migration/V58__employee_daily_closing_expenses.sql](../../../backend/src/main/resources/db/migration/V58__employee_daily_closing_expenses.sql) |
| comment | VARCHAR(1000) | [backend/src/main/resources/db/migration/V58__employee_daily_closing_expenses.sql](../../../backend/src/main/resources/db/migration/V58__employee_daily_closing_expenses.sql) |
| created_at | TIMESTAMPTZ NOT NULL | [backend/src/main/resources/db/migration/V58__employee_daily_closing_expenses.sql](../../../backend/src/main/resources/db/migration/V58__employee_daily_closing_expenses.sql) |
| created_by | VARCHAR(200) | [backend/src/main/resources/db/migration/V63__company_operational_closing_scope.sql](../../../backend/src/main/resources/db/migration/V63__company_operational_closing_scope.sql) |
| expense_id | UUID NOT NULL | [backend/src/main/resources/db/migration/V58__employee_daily_closing_expenses.sql](../../../backend/src/main/resources/db/migration/V58__employee_daily_closing_expenses.sql) |
| payment_method | VARCHAR(20) NOT NULL | [backend/src/main/resources/db/migration/V58__employee_daily_closing_expenses.sql](../../../backend/src/main/resources/db/migration/V58__employee_daily_closing_expenses.sql) |

### employee_daily_closing_sales

| Column | Last recognized declaration | Source |
|---|---|---|
| closing_id | UUID NOT NULL REFERENCES employee_daily_closings(id) | [backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql](../../../backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql) |
| completed_at | TIMESTAMPTZ NOT NULL | [backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql](../../../backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql) |
| goods_total | NUMERIC(19,2), | [backend/src/main/resources/db/migration/V1005__freeze_employee_closing_sale_components.sql](../../../backend/src/main/resources/db/migration/V1005__freeze_employee_closing_sale_components.sql) |
| products | TEXT, | [backend/src/main/resources/db/migration/V62__employee_closing_sale_identity_snapshot.sql](../../../backend/src/main/resources/db/migration/V62__employee_closing_sale_identity_snapshot.sql) |
| quantity | BIGINT NOT NULL | [backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql](../../../backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql) |
| sale_id | UUID NOT NULL | [backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql](../../../backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql) |
| sale_number | VARCHAR(30) NOT NULL | [backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql](../../../backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql) |
| total | NUMERIC(19,2) NOT NULL | [backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql](../../../backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql) |

### employee_daily_closings

| Column | Last recognized declaration | Source |
|---|---|---|
| actual_bank | NUMERIC(19,2) NOT NULL | [backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql](../../../backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql) |
| actual_cash | NUMERIC(19,2) NOT NULL | [backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql](../../../backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql) |
| actual_total | NUMERIC(19,2), | [backend/src/main/resources/db/migration/V61__employee_daily_close_single_total.sql](../../../backend/src/main/resources/db/migration/V61__employee_daily_close_single_total.sql) |
| bank_note | VARCHAR(2000) | [backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql](../../../backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql) |
| business_date | DATE NOT NULL | [backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql](../../../backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql) |
| cash_note | VARCHAR(2000) | [backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql](../../../backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql) |
| company_wide | BOOLEAN NOT NULL DEFAULT FALSE | [backend/src/main/resources/db/migration/V63__company_operational_closing_scope.sql](../../../backend/src/main/resources/db/migration/V63__company_operational_closing_scope.sql) |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp() | [backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql](../../../backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql) |
| employee_id | UUID NOT NULL REFERENCES app_users(id) | [backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql](../../../backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql) |
| expected_bank | NUMERIC(19,2) NOT NULL | [backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql](../../../backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql) |
| expected_cash | NUMERIC(19,2) NOT NULL | [backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql](../../../backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql](../../../backend/src/main/resources/db/migration/V53__personal_employee_daily_closing.sql) |

### expense_accrual_payments

| Column | Last recognized declaration | Source |
|---|---|---|
| accrual_id | UUID NOT NULL REFERENCES expense_accruals(id) ON DELETE RESTRICT | [backend/src/main/resources/db/migration/V1001__expense_accrual_payments.sql](../../../backend/src/main/resources/db/migration/V1001__expense_accrual_payments.sql) |
| expense_id | UUID NOT NULL UNIQUE REFERENCES expenses(id) ON DELETE RESTRICT | [backend/src/main/resources/db/migration/V1001__expense_accrual_payments.sql](../../../backend/src/main/resources/db/migration/V1001__expense_accrual_payments.sql) |
| linked_at | TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp() | [backend/src/main/resources/db/migration/V1001__expense_accrual_payments.sql](../../../backend/src/main/resources/db/migration/V1001__expense_accrual_payments.sql) |
| linked_by | VARCHAR(255) NOT NULL | [backend/src/main/resources/db/migration/V1001__expense_accrual_payments.sql](../../../backend/src/main/resources/db/migration/V1001__expense_accrual_payments.sql) |

### expense_accruals

| Column | Last recognized declaration | Source |
|---|---|---|
| amount | NUMERIC(19,0) NOT NULL CHECK (amount > 0) | [backend/src/main/resources/db/migration/V1000__expense_accruals.sql](../../../backend/src/main/resources/db/migration/V1000__expense_accruals.sql) |
| category | VARCHAR(40) NOT NULL CHECK (category IN ('ADVERTISING','RENT','SALARY','DELIVERY','UTILITIES','OTHER')) | [backend/src/main/resources/db/migration/V1000__expense_accruals.sql](../../../backend/src/main/resources/db/migration/V1000__expense_accruals.sql) |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp() | [backend/src/main/resources/db/migration/V1000__expense_accruals.sql](../../../backend/src/main/resources/db/migration/V1000__expense_accruals.sql) |
| created_by | VARCHAR(255) NOT NULL | [backend/src/main/resources/db/migration/V1000__expense_accruals.sql](../../../backend/src/main/resources/db/migration/V1000__expense_accruals.sql) |
| description | VARCHAR(1000) NOT NULL | [backend/src/main/resources/db/migration/V1000__expense_accruals.sql](../../../backend/src/main/resources/db/migration/V1000__expense_accruals.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V1000__expense_accruals.sql](../../../backend/src/main/resources/db/migration/V1000__expense_accruals.sql) |
| idempotency_key | UUID NOT NULL UNIQUE | [backend/src/main/resources/db/migration/V1000__expense_accruals.sql](../../../backend/src/main/resources/db/migration/V1000__expense_accruals.sql) |
| period_end | DATE NOT NULL | [backend/src/main/resources/db/migration/V1000__expense_accruals.sql](../../../backend/src/main/resources/db/migration/V1000__expense_accruals.sql) |
| period_start | DATE NOT NULL | [backend/src/main/resources/db/migration/V1000__expense_accruals.sql](../../../backend/src/main/resources/db/migration/V1000__expense_accruals.sql) |

### expenses

| Column | Last recognized declaration | Source |
|---|---|---|
| amount | NUMERIC(19, 2) NOT NULL | [backend/src/main/resources/db/migration/V20__create_expenses.sql](../../../backend/src/main/resources/db/migration/V20__create_expenses.sql) |
| category | VARCHAR(30) NOT NULL | [backend/src/main/resources/db/migration/V20__create_expenses.sql](../../../backend/src/main/resources/db/migration/V20__create_expenses.sql) |
| comment | VARCHAR(1000) | [backend/src/main/resources/db/migration/V20__create_expenses.sql](../../../backend/src/main/resources/db/migration/V20__create_expenses.sql) |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP | [backend/src/main/resources/db/migration/V20__create_expenses.sql](../../../backend/src/main/resources/db/migration/V20__create_expenses.sql) |
| created_by | VARCHAR(200) NOT NULL | [backend/src/main/resources/db/migration/V20__create_expenses.sql](../../../backend/src/main/resources/db/migration/V20__create_expenses.sql) |
| expense_date | DATE NOT NULL | [backend/src/main/resources/db/migration/V20__create_expenses.sql](../../../backend/src/main/resources/db/migration/V20__create_expenses.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V20__create_expenses.sql](../../../backend/src/main/resources/db/migration/V20__create_expenses.sql) |
| idempotency_key | UUID | [backend/src/main/resources/db/migration/V35__expense_idempotency.sql](../../../backend/src/main/resources/db/migration/V35__expense_idempotency.sql) |
| operational_report_date | DATE | [backend/src/main/resources/db/migration/V65__expense_operational_reporting_date.sql](../../../backend/src/main/resources/db/migration/V65__expense_operational_reporting_date.sql) |
| payment_method | VARCHAR(20) NOT NULL | [backend/src/main/resources/db/migration/V20__create_expenses.sql](../../../backend/src/main/resources/db/migration/V20__create_expenses.sql) |
| request_fingerprint | VARCHAR(64) | [backend/src/main/resources/db/migration/V35__expense_idempotency.sql](../../../backend/src/main/resources/db/migration/V35__expense_idempotency.sql) |

### finance_accounts

| Column | Last recognized declaration | Source |
|---|---|---|
| balance | NUMERIC(19, 2) NOT NULL DEFAULT 0 | [backend/src/main/resources/db/migration/V24__create_finance_accounts.sql](../../../backend/src/main/resources/db/migration/V24__create_finance_accounts.sql) |
| code | VARCHAR(10) PRIMARY KEY | [backend/src/main/resources/db/migration/V24__create_finance_accounts.sql](../../../backend/src/main/resources/db/migration/V24__create_finance_accounts.sql) |
| opening_balance_initialized | BOOLEAN NOT NULL DEFAULT FALSE | [backend/src/main/resources/db/migration/V26__finance_account_initialization.sql](../../../backend/src/main/resources/db/migration/V26__finance_account_initialization.sql) |

### finance_admin_adjustments

| Column | Last recognized declaration | Source |
|---|---|---|
| account_code | VARCHAR(20) NOT NULL REFERENCES finance_accounts(code) | [backend/src/main/resources/db/migration/V56__finance_admin_adjustments.sql](../../../backend/src/main/resources/db/migration/V56__finance_admin_adjustments.sql) |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp() | [backend/src/main/resources/db/migration/V56__finance_admin_adjustments.sql](../../../backend/src/main/resources/db/migration/V56__finance_admin_adjustments.sql) |
| created_by | VARCHAR(200) NOT NULL | [backend/src/main/resources/db/migration/V56__finance_admin_adjustments.sql](../../../backend/src/main/resources/db/migration/V56__finance_admin_adjustments.sql) |
| delta | NUMERIC(19,2) NOT NULL | [backend/src/main/resources/db/migration/V56__finance_admin_adjustments.sql](../../../backend/src/main/resources/db/migration/V56__finance_admin_adjustments.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V56__finance_admin_adjustments.sql](../../../backend/src/main/resources/db/migration/V56__finance_admin_adjustments.sql) |
| new_balance | NUMERIC(19,2) NOT NULL | [backend/src/main/resources/db/migration/V56__finance_admin_adjustments.sql](../../../backend/src/main/resources/db/migration/V56__finance_admin_adjustments.sql) |
| previous_balance | NUMERIC(19,2) NOT NULL | [backend/src/main/resources/db/migration/V56__finance_admin_adjustments.sql](../../../backend/src/main/resources/db/migration/V56__finance_admin_adjustments.sql) |
| reason | VARCHAR(500) NOT NULL CHECK (length(btrim(reason)) >= 5) | [backend/src/main/resources/db/migration/V56__finance_admin_adjustments.sql](../../../backend/src/main/resources/db/migration/V56__finance_admin_adjustments.sql) |

### finance_daily_closing_access_locks

| Column | Last recognized declaration | Source |
|---|---|---|
| business_date | DATE PRIMARY KEY | [backend/src/main/resources/db/migration/V41__daily_closing_unlocks.sql](../../../backend/src/main/resources/db/migration/V41__daily_closing_unlocks.sql) |

### finance_daily_closing_accounts

| Column | Last recognized declaration | Source |
|---|---|---|
| account_code | VARCHAR(10) NOT NULL REFERENCES finance_accounts(code) | [backend/src/main/resources/db/migration/V32__daily_finance_closing.sql](../../../backend/src/main/resources/db/migration/V32__daily_finance_closing.sql) |
| actual_balance | NUMERIC(19,2) NOT NULL | [backend/src/main/resources/db/migration/V32__daily_finance_closing.sql](../../../backend/src/main/resources/db/migration/V32__daily_finance_closing.sql) |
| closing_id | UUID NOT NULL REFERENCES finance_daily_closings(id) | [backend/src/main/resources/db/migration/V32__daily_finance_closing.sql](../../../backend/src/main/resources/db/migration/V32__daily_finance_closing.sql) |
| difference | NUMERIC(19,2) GENERATED ALWAYS AS (actual_balance - expected_balance) STORED | [backend/src/main/resources/db/migration/V32__daily_finance_closing.sql](../../../backend/src/main/resources/db/migration/V32__daily_finance_closing.sql) |
| expected_balance | NUMERIC(19,2) NOT NULL | [backend/src/main/resources/db/migration/V32__daily_finance_closing.sql](../../../backend/src/main/resources/db/migration/V32__daily_finance_closing.sql) |
| note | VARCHAR(2000) | [backend/src/main/resources/db/migration/V32__daily_finance_closing.sql](../../../backend/src/main/resources/db/migration/V32__daily_finance_closing.sql) |

### finance_daily_closing_adjustments

| Column | Last recognized declaration | Source |
|---|---|---|
| account_code | VARCHAR(10) NOT NULL | [backend/src/main/resources/db/migration/V45__daily_closing_recalculated_balances.sql](../../../backend/src/main/resources/db/migration/V45__daily_closing_recalculated_balances.sql) |
| adjusted_at | TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp() | [backend/src/main/resources/db/migration/V45__daily_closing_recalculated_balances.sql](../../../backend/src/main/resources/db/migration/V45__daily_closing_recalculated_balances.sql) |
| adjusted_by | VARCHAR(200) NOT NULL | [backend/src/main/resources/db/migration/V45__daily_closing_recalculated_balances.sql](../../../backend/src/main/resources/db/migration/V45__daily_closing_recalculated_balances.sql) |
| closing_id | UUID NOT NULL | [backend/src/main/resources/db/migration/V45__daily_closing_recalculated_balances.sql](../../../backend/src/main/resources/db/migration/V45__daily_closing_recalculated_balances.sql) |
| expected_balance | NUMERIC(19,2) NOT NULL | [backend/src/main/resources/db/migration/V45__daily_closing_recalculated_balances.sql](../../../backend/src/main/resources/db/migration/V45__daily_closing_recalculated_balances.sql) |

### finance_daily_closing_sales

| Column | Last recognized declaration | Source |
|---|---|---|
| address | VARCHAR(500) | [backend/src/main/resources/db/migration/V50__p22_daily_closing_sales_snapshot.sql](../../../backend/src/main/resources/db/migration/V50__p22_daily_closing_sales_snapshot.sql) |
| closing_id | UUID NOT NULL REFERENCES finance_daily_closing_sales_snapshots(closing_id) ON DELETE CASCADE | [backend/src/main/resources/db/migration/V50__p22_daily_closing_sales_snapshot.sql](../../../backend/src/main/resources/db/migration/V50__p22_daily_closing_sales_snapshot.sql) |
| completed_at | TIMESTAMPTZ NOT NULL | [backend/src/main/resources/db/migration/V50__p22_daily_closing_sales_snapshot.sql](../../../backend/src/main/resources/db/migration/V50__p22_daily_closing_sales_snapshot.sql) |
| customer_name | VARCHAR(200) | [backend/src/main/resources/db/migration/V50__p22_daily_closing_sales_snapshot.sql](../../../backend/src/main/resources/db/migration/V50__p22_daily_closing_sales_snapshot.sql) |
| delivery_status | VARCHAR(30) | [backend/src/main/resources/db/migration/V66__finance_closing_sales_delivery_status.sql](../../../backend/src/main/resources/db/migration/V66__finance_closing_sales_delivery_status.sql) |
| fulfillment_type | VARCHAR(30) NOT NULL | [backend/src/main/resources/db/migration/V50__p22_daily_closing_sales_snapshot.sql](../../../backend/src/main/resources/db/migration/V50__p22_daily_closing_sales_snapshot.sql) |
| payment_status | VARCHAR(16) NOT NULL | [backend/src/main/resources/db/migration/V50__p22_daily_closing_sales_snapshot.sql](../../../backend/src/main/resources/db/migration/V50__p22_daily_closing_sales_snapshot.sql) |
| phone | VARCHAR(50) | [backend/src/main/resources/db/migration/V50__p22_daily_closing_sales_snapshot.sql](../../../backend/src/main/resources/db/migration/V50__p22_daily_closing_sales_snapshot.sql) |
| products | TEXT | [backend/src/main/resources/db/migration/V50__p22_daily_closing_sales_snapshot.sql](../../../backend/src/main/resources/db/migration/V50__p22_daily_closing_sales_snapshot.sql) |
| quantity | BIGINT NOT NULL CHECK(quantity >= 0) | [backend/src/main/resources/db/migration/V50__p22_daily_closing_sales_snapshot.sql](../../../backend/src/main/resources/db/migration/V50__p22_daily_closing_sales_snapshot.sql) |
| sale_id | UUID NOT NULL | [backend/src/main/resources/db/migration/V50__p22_daily_closing_sales_snapshot.sql](../../../backend/src/main/resources/db/migration/V50__p22_daily_closing_sales_snapshot.sql) |
| total | NUMERIC(25,2) NOT NULL CHECK(total >= 0) | [backend/src/main/resources/db/migration/V50__p22_daily_closing_sales_snapshot.sql](../../../backend/src/main/resources/db/migration/V50__p22_daily_closing_sales_snapshot.sql) |

### finance_daily_closing_sales_snapshots

| Column | Last recognized declaration | Source |
|---|---|---|
| captured_at | TIMESTAMPTZ NOT NULL | [backend/src/main/resources/db/migration/V50__p22_daily_closing_sales_snapshot.sql](../../../backend/src/main/resources/db/migration/V50__p22_daily_closing_sales_snapshot.sql) |
| closing_id | UUID PRIMARY KEY REFERENCES finance_daily_closings(id) ON DELETE CASCADE | [backend/src/main/resources/db/migration/V50__p22_daily_closing_sales_snapshot.sql](../../../backend/src/main/resources/db/migration/V50__p22_daily_closing_sales_snapshot.sql) |

### finance_daily_closing_unlocks

| Column | Last recognized declaration | Source |
|---|---|---|
| business_date | DATE NOT NULL REFERENCES finance_daily_closing_access_locks(business_date) | [backend/src/main/resources/db/migration/V41__daily_closing_unlocks.sql](../../../backend/src/main/resources/db/migration/V41__daily_closing_unlocks.sql) |
| expires_at | TIMESTAMPTZ NOT NULL | [backend/src/main/resources/db/migration/V41__daily_closing_unlocks.sql](../../../backend/src/main/resources/db/migration/V41__daily_closing_unlocks.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V41__daily_closing_unlocks.sql](../../../backend/src/main/resources/db/migration/V41__daily_closing_unlocks.sql) |
| reason | VARCHAR(2000) NOT NULL | [backend/src/main/resources/db/migration/V41__daily_closing_unlocks.sql](../../../backend/src/main/resources/db/migration/V41__daily_closing_unlocks.sql) |
| revoked_at | TIMESTAMPTZ | [backend/src/main/resources/db/migration/V41__daily_closing_unlocks.sql](../../../backend/src/main/resources/db/migration/V41__daily_closing_unlocks.sql) |
| revoked_by | VARCHAR(100) | [backend/src/main/resources/db/migration/V41__daily_closing_unlocks.sql](../../../backend/src/main/resources/db/migration/V41__daily_closing_unlocks.sql) |
| unlocked_at | TIMESTAMPTZ NOT NULL | [backend/src/main/resources/db/migration/V41__daily_closing_unlocks.sql](../../../backend/src/main/resources/db/migration/V41__daily_closing_unlocks.sql) |
| unlocked_by | VARCHAR(100) NOT NULL | [backend/src/main/resources/db/migration/V41__daily_closing_unlocks.sql](../../../backend/src/main/resources/db/migration/V41__daily_closing_unlocks.sql) |

### finance_daily_closings

| Column | Last recognized declaration | Source |
|---|---|---|
| business_date | DATE NOT NULL UNIQUE | [backend/src/main/resources/db/migration/V32__daily_finance_closing.sql](../../../backend/src/main/resources/db/migration/V32__daily_finance_closing.sql) |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP | [backend/src/main/resources/db/migration/V32__daily_finance_closing.sql](../../../backend/src/main/resources/db/migration/V32__daily_finance_closing.sql) |
| created_by | VARCHAR(200) NOT NULL | [backend/src/main/resources/db/migration/V32__daily_finance_closing.sql](../../../backend/src/main/resources/db/migration/V32__daily_finance_closing.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V32__daily_finance_closing.sql](../../../backend/src/main/resources/db/migration/V32__daily_finance_closing.sql) |
| version | BIGINT NOT NULL DEFAULT 0 | [backend/src/main/resources/db/migration/V44__daily_closing_version.sql](../../../backend/src/main/resources/db/migration/V44__daily_closing_version.sql) |

### finance_movements

| Column | Last recognized declaration | Source |
|---|---|---|
| account_code | VARCHAR(10) NOT NULL REFERENCES finance_accounts(code) | [backend/src/main/resources/db/migration/V24__create_finance_accounts.sql](../../../backend/src/main/resources/db/migration/V24__create_finance_accounts.sql) |
| amount | NUMERIC(19, 2) NOT NULL | [backend/src/main/resources/db/migration/V24__create_finance_accounts.sql](../../../backend/src/main/resources/db/migration/V24__create_finance_accounts.sql) |
| business_date | DATE | [backend/src/main/resources/db/migration/V43__finance_movement_business_date.sql](../../../backend/src/main/resources/db/migration/V43__finance_movement_business_date.sql) |
| business_date_source | VARCHAR(32) | [backend/src/main/resources/db/migration/V43__finance_movement_business_date.sql](../../../backend/src/main/resources/db/migration/V43__finance_movement_business_date.sql) |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP | [backend/src/main/resources/db/migration/V24__create_finance_accounts.sql](../../../backend/src/main/resources/db/migration/V24__create_finance_accounts.sql) |
| created_by | VARCHAR(200) NOT NULL | [backend/src/main/resources/db/migration/V24__create_finance_accounts.sql](../../../backend/src/main/resources/db/migration/V24__create_finance_accounts.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V24__create_finance_accounts.sql](../../../backend/src/main/resources/db/migration/V24__create_finance_accounts.sql) |
| movement_type | VARCHAR(30) NOT NULL | [backend/src/main/resources/db/migration/V24__create_finance_accounts.sql](../../../backend/src/main/resources/db/migration/V24__create_finance_accounts.sql) |
| source_id | UUID NOT NULL | [backend/src/main/resources/db/migration/V24__create_finance_accounts.sql](../../../backend/src/main/resources/db/migration/V24__create_finance_accounts.sql) |
| source_type | VARCHAR(30) NOT NULL | [backend/src/main/resources/db/migration/V24__create_finance_accounts.sql](../../../backend/src/main/resources/db/migration/V24__create_finance_accounts.sql) |

### finance_transfers

| Column | Last recognized declaration | Source |
|---|---|---|
| amount | NUMERIC(19, 2) NOT NULL | [backend/src/main/resources/db/migration/V27__create_finance_transfers.sql](../../../backend/src/main/resources/db/migration/V27__create_finance_transfers.sql) |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP | [backend/src/main/resources/db/migration/V27__create_finance_transfers.sql](../../../backend/src/main/resources/db/migration/V27__create_finance_transfers.sql) |
| created_by | VARCHAR(200) NOT NULL | [backend/src/main/resources/db/migration/V27__create_finance_transfers.sql](../../../backend/src/main/resources/db/migration/V27__create_finance_transfers.sql) |
| from_account | VARCHAR(10) NOT NULL REFERENCES finance_accounts(code) | [backend/src/main/resources/db/migration/V27__create_finance_transfers.sql](../../../backend/src/main/resources/db/migration/V27__create_finance_transfers.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V27__create_finance_transfers.sql](../../../backend/src/main/resources/db/migration/V27__create_finance_transfers.sql) |
| to_account | VARCHAR(10) NOT NULL REFERENCES finance_accounts(code) | [backend/src/main/resources/db/migration/V27__create_finance_transfers.sql](../../../backend/src/main/resources/db/migration/V27__create_finance_transfers.sql) |

### inventory_balances

| Column | Last recognized declaration | Source |
|---|---|---|
| blocked | BIGINT NOT NULL DEFAULT 0 | [backend/src/main/resources/db/migration/V3__create_inventory.sql](../../../backend/src/main/resources/db/migration/V3__create_inventory.sql) |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP | [backend/src/main/resources/db/migration/V3__create_inventory.sql](../../../backend/src/main/resources/db/migration/V3__create_inventory.sql) |
| on_hand | BIGINT NOT NULL DEFAULT 0 | [backend/src/main/resources/db/migration/V3__create_inventory.sql](../../../backend/src/main/resources/db/migration/V3__create_inventory.sql) |
| product_variant_id | UUID NOT NULL REFERENCES product_variants(id) | [backend/src/main/resources/db/migration/V3__create_inventory.sql](../../../backend/src/main/resources/db/migration/V3__create_inventory.sql) |
| reserved | BIGINT NOT NULL DEFAULT 0 | [backend/src/main/resources/db/migration/V3__create_inventory.sql](../../../backend/src/main/resources/db/migration/V3__create_inventory.sql) |
| updated_at | TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP | [backend/src/main/resources/db/migration/V3__create_inventory.sql](../../../backend/src/main/resources/db/migration/V3__create_inventory.sql) |
| warehouse_id | UUID NOT NULL REFERENCES warehouses(id) | [backend/src/main/resources/db/migration/V3__create_inventory.sql](../../../backend/src/main/resources/db/migration/V3__create_inventory.sql) |

### inventory_cost_allocations

| Column | Last recognized declaration | Source |
|---|---|---|
| allocated_cost | NUMERIC(38, 2) NOT NULL CHECK (allocated_cost >= 0) | [backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql](../../../backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql) |
| cost_layer_id | UUID NOT NULL REFERENCES inventory_cost_layers(id) | [backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql](../../../backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql) |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP | [backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql](../../../backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql](../../../backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql) |
| quantity | BIGINT NOT NULL CHECK (quantity > 0) | [backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql](../../../backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql) |
| stock_movement_id | UUID NOT NULL REFERENCES stock_movements(id) | [backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql](../../../backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql) |

### inventory_cost_layers

| Column | Last recognized declaration | Source |
|---|---|---|
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql](../../../backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql) |
| product_variant_id | UUID NOT NULL REFERENCES product_variants(id) | [backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql](../../../backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql) |
| quantity_received | BIGINT NOT NULL | [backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql](../../../backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql) |
| quantity_remaining | BIGINT NOT NULL | [backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql](../../../backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql) |
| received_at | TIMESTAMPTZ NOT NULL | [backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql](../../../backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql) |
| remaining_cost | NUMERIC(38, 2) NOT NULL | [backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql](../../../backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql) |
| source_movement_id | UUID NOT NULL UNIQUE REFERENCES stock_movements(id) | [backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql](../../../backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql) |
| total_cost | NUMERIC(38, 2) NOT NULL | [backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql](../../../backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql) |
| warehouse_id | UUID NOT NULL REFERENCES warehouses(id) | [backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql](../../../backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql) |

### inventory_cost_movements

| Column | Last recognized declaration | Source |
|---|---|---|
| amount | NUMERIC(38, 2) NOT NULL CHECK (amount >= 0) | [backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql](../../../backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql) |
| cost_layer_id | UUID NOT NULL REFERENCES inventory_cost_layers(id) | [backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql](../../../backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql) |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP | [backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql](../../../backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql) |
| direction | VARCHAR(10) NOT NULL CHECK (direction IN ('IN', 'OUT')) | [backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql](../../../backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql](../../../backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql) |
| quantity | BIGINT NOT NULL CHECK (quantity > 0) | [backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql](../../../backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql) |
| stock_movement_id | UUID NOT NULL REFERENCES stock_movements(id) | [backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql](../../../backend/src/main/resources/db/migration/V21__create_inventory_cost_layers.sql) |

### inventory_cost_restorations

| Column | Last recognized declaration | Source |
|---|---|---|
| amount | NUMERIC(38,2) NOT NULL CHECK (amount >= 0) | [backend/src/main/resources/db/migration/V22__restore_and_protect_inventory_cost.sql](../../../backend/src/main/resources/db/migration/V22__restore_and_protect_inventory_cost.sql) |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp() | [backend/src/main/resources/db/migration/V22__restore_and_protect_inventory_cost.sql](../../../backend/src/main/resources/db/migration/V22__restore_and_protect_inventory_cost.sql) |
| original_allocation_id | UUID NOT NULL REFERENCES inventory_cost_allocations(id) | [backend/src/main/resources/db/migration/V22__restore_and_protect_inventory_cost.sql](../../../backend/src/main/resources/db/migration/V22__restore_and_protect_inventory_cost.sql) |
| quantity | BIGINT NOT NULL CHECK (quantity > 0) | [backend/src/main/resources/db/migration/V22__restore_and_protect_inventory_cost.sql](../../../backend/src/main/resources/db/migration/V22__restore_and_protect_inventory_cost.sql) |
| return_movement_id | UUID NOT NULL REFERENCES stock_movements(id) | [backend/src/main/resources/db/migration/V22__restore_and_protect_inventory_cost.sql](../../../backend/src/main/resources/db/migration/V22__restore_and_protect_inventory_cost.sql) |

### inventory_cost_write_offs

| Column | Last recognized declaration | Source |
|---|---|---|
| purchase_receipt_item_id | UUID REFERENCES purchase_receipt_items(id) | [backend/src/main/resources/db/migration/V22__restore_and_protect_inventory_cost.sql](../../../backend/src/main/resources/db/migration/V22__restore_and_protect_inventory_cost.sql) |
| stock_movement_id | UUID PRIMARY KEY REFERENCES stock_movements(id) | [backend/src/main/resources/db/migration/V22__restore_and_protect_inventory_cost.sql](../../../backend/src/main/resources/db/migration/V22__restore_and_protect_inventory_cost.sql) |

### inventory_transfer_cost_origins

| Column | Last recognized declaration | Source |
|---|---|---|
| amount | NUMERIC(38, 2) NOT NULL CHECK (amount >= 0) | [backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql](../../../backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql) |
| quantity | BIGINT NOT NULL CHECK (quantity > 0) | [backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql](../../../backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql) |
| source_allocation_id | UUID NOT NULL UNIQUE REFERENCES inventory_cost_allocations(id) | [backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql](../../../backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql) |
| transfer_id | UUID NOT NULL REFERENCES inventory_transfers(id) | [backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql](../../../backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql) |

### inventory_transfers

| Column | Last recognized declaration | Source |
|---|---|---|
| actor | VARCHAR(200) | [backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql](../../../backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql) |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp() | [backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql](../../../backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql) |
| destination_warehouse_id | UUID NOT NULL REFERENCES warehouses(id) | [backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql](../../../backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql](../../../backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql) |
| in_movement_id | UUID UNIQUE REFERENCES stock_movements(id) | [backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql](../../../backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql) |
| out_movement_id | UUID UNIQUE REFERENCES stock_movements(id) | [backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql](../../../backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql) |
| product_variant_id | UUID NOT NULL REFERENCES product_variants(id) | [backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql](../../../backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql) |
| quantity | BIGINT NOT NULL CHECK (quantity > 0) | [backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql](../../../backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql) |
| source_warehouse_id | UUID NOT NULL REFERENCES warehouses(id) | [backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql](../../../backend/src/main/resources/db/migration/V23__create_inventory_transfers.sql) |

### payments

| Column | Last recognized declaration | Source |
|---|---|---|
| amount | numeric(19, 2) not null | [backend/src/main/resources/db/migration/V12__create_payments.sql](../../../backend/src/main/resources/db/migration/V12__create_payments.sql) |
| cancellation_reason | varchar(1000) | [backend/src/main/resources/db/migration/V12__create_payments.sql](../../../backend/src/main/resources/db/migration/V12__create_payments.sql) |
| cancelled_at | timestamptz | [backend/src/main/resources/db/migration/V12__create_payments.sql](../../../backend/src/main/resources/db/migration/V12__create_payments.sql) |
| cancelled_by | varchar(200) | [backend/src/main/resources/db/migration/V12__create_payments.sql](../../../backend/src/main/resources/db/migration/V12__create_payments.sql) |
| comment | varchar(1000) | [backend/src/main/resources/db/migration/V12__create_payments.sql](../../../backend/src/main/resources/db/migration/V12__create_payments.sql) |
| id | uuid primary key | [backend/src/main/resources/db/migration/V12__create_payments.sql](../../../backend/src/main/resources/db/migration/V12__create_payments.sql) |
| method | varchar(30) not null | [backend/src/main/resources/db/migration/V12__create_payments.sql](../../../backend/src/main/resources/db/migration/V12__create_payments.sql) |
| paid_at | timestamptz not null | [backend/src/main/resources/db/migration/V12__create_payments.sql](../../../backend/src/main/resources/db/migration/V12__create_payments.sql) |
| paid_by | varchar(200) not null | [backend/src/main/resources/db/migration/V12__create_payments.sql](../../../backend/src/main/resources/db/migration/V12__create_payments.sql) |
| payment_channel | VARCHAR(30) | [backend/src/main/resources/db/migration/V38__sale_payment_channel.sql](../../../backend/src/main/resources/db/migration/V38__sale_payment_channel.sql) |
| reference | varchar(200) | [backend/src/main/resources/db/migration/V12__create_payments.sql](../../../backend/src/main/resources/db/migration/V12__create_payments.sql) |
| sale_id | uuid not null | [backend/src/main/resources/db/migration/V12__create_payments.sql](../../../backend/src/main/resources/db/migration/V12__create_payments.sql) |
| status | varchar(30) not null | [backend/src/main/resources/db/migration/V12__create_payments.sql](../../../backend/src/main/resources/db/migration/V12__create_payments.sql) |

### product_variants

| Column | Last recognized declaration | Source |
|---|---|---|
| active | BOOLEAN NOT NULL | [backend/src/main/resources/db/migration/V1__create_product_catalog.sql](../../../backend/src/main/resources/db/migration/V1__create_product_catalog.sql) |
| color | VARCHAR(100) | [backend/src/main/resources/db/migration/V1__create_product_catalog.sql](../../../backend/src/main/resources/db/migration/V1__create_product_catalog.sql) |
| created_at | TIMESTAMPTZ NOT NULL | [backend/src/main/resources/db/migration/V1__create_product_catalog.sql](../../../backend/src/main/resources/db/migration/V1__create_product_catalog.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V1__create_product_catalog.sql](../../../backend/src/main/resources/db/migration/V1__create_product_catalog.sql) |
| name | VARCHAR(200) NOT NULL CHECK (length(btrim(name)) > 0) | [backend/src/main/resources/db/migration/V1__create_product_catalog.sql](../../../backend/src/main/resources/db/migration/V1__create_product_catalog.sql) |
| product_id | UUID NOT NULL REFERENCES products(id) | [backend/src/main/resources/db/migration/V1__create_product_catalog.sql](../../../backend/src/main/resources/db/migration/V1__create_product_catalog.sql) |
| recommended_sale_price | NUMERIC(19, 2) NOT NULL CHECK (recommended_sale_price >= 0) | [backend/src/main/resources/db/migration/V1__create_product_catalog.sql](../../../backend/src/main/resources/db/migration/V1__create_product_catalog.sql) |
| sku | VARCHAR(100) | [backend/src/main/resources/db/migration/V1__create_product_catalog.sql](../../../backend/src/main/resources/db/migration/V1__create_product_catalog.sql) |
| updated_at | TIMESTAMPTZ NOT NULL | [backend/src/main/resources/db/migration/V1__create_product_catalog.sql](../../../backend/src/main/resources/db/migration/V1__create_product_catalog.sql) |
| version | BIGINT NOT NULL DEFAULT 0 | [backend/src/main/resources/db/migration/V1__create_product_catalog.sql](../../../backend/src/main/resources/db/migration/V1__create_product_catalog.sql) |

### products

| Column | Last recognized declaration | Source |
|---|---|---|
| active | BOOLEAN NOT NULL | [backend/src/main/resources/db/migration/V1__create_product_catalog.sql](../../../backend/src/main/resources/db/migration/V1__create_product_catalog.sql) |
| category | VARCHAR(120) | [backend/src/main/resources/db/migration/V1__create_product_catalog.sql](../../../backend/src/main/resources/db/migration/V1__create_product_catalog.sql) |
| created_at | TIMESTAMPTZ NOT NULL | [backend/src/main/resources/db/migration/V1__create_product_catalog.sql](../../../backend/src/main/resources/db/migration/V1__create_product_catalog.sql) |
| description | VARCHAR(4000) | [backend/src/main/resources/db/migration/V1__create_product_catalog.sql](../../../backend/src/main/resources/db/migration/V1__create_product_catalog.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V1__create_product_catalog.sql](../../../backend/src/main/resources/db/migration/V1__create_product_catalog.sql) |
| name | VARCHAR(200) NOT NULL CHECK (length(btrim(name)) > 0) | [backend/src/main/resources/db/migration/V1__create_product_catalog.sql](../../../backend/src/main/resources/db/migration/V1__create_product_catalog.sql) |
| updated_at | TIMESTAMPTZ NOT NULL | [backend/src/main/resources/db/migration/V1__create_product_catalog.sql](../../../backend/src/main/resources/db/migration/V1__create_product_catalog.sql) |
| version | BIGINT NOT NULL DEFAULT 0 | [backend/src/main/resources/db/migration/V1__create_product_catalog.sql](../../../backend/src/main/resources/db/migration/V1__create_product_catalog.sql) |

### purchase_items

| Column | Last recognized declaration | Source |
|---|---|---|
| allocated_cargo_cost | NUMERIC(19,2) CHECK (allocated_cargo_cost >= 0) | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| final_unit_cost | NUMERIC(25,6) CHECK (final_unit_cost >= 0) | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| line_number | INTEGER NOT NULL CHECK (line_number > 0) | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| ordered_quantity | BIGINT NOT NULL CHECK (ordered_quantity > 0) | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| product_variant_id | UUID NOT NULL REFERENCES product_variants(id) | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| purchase_id | UUID NOT NULL REFERENCES purchases(id) | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| purchase_unit_cost | NUMERIC(19,2) NOT NULL CHECK (purchase_unit_cost >= 0) | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| received_quantity | BIGINT NOT NULL DEFAULT 0 CHECK (received_quantity >= 0 AND received_quantity <= ordered_quantity) | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |

### purchase_payments

| Column | Last recognized declaration | Source |
|---|---|---|
| account_code | VARCHAR(10) NOT NULL REFERENCES finance_accounts(code) | [backend/src/main/resources/db/migration/V37__purchase_payments.sql](../../../backend/src/main/resources/db/migration/V37__purchase_payments.sql) |
| amount | NUMERIC(19,2) NOT NULL CHECK (amount > 0 AND amount = trunc(amount)) | [backend/src/main/resources/db/migration/V37__purchase_payments.sql](../../../backend/src/main/resources/db/migration/V37__purchase_payments.sql) |
| comment | VARCHAR(1000) | [backend/src/main/resources/db/migration/V37__purchase_payments.sql](../../../backend/src/main/resources/db/migration/V37__purchase_payments.sql) |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp() | [backend/src/main/resources/db/migration/V37__purchase_payments.sql](../../../backend/src/main/resources/db/migration/V37__purchase_payments.sql) |
| created_by | VARCHAR(200) NOT NULL | [backend/src/main/resources/db/migration/V37__purchase_payments.sql](../../../backend/src/main/resources/db/migration/V37__purchase_payments.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V37__purchase_payments.sql](../../../backend/src/main/resources/db/migration/V37__purchase_payments.sql) |
| idempotency_key | UUID NOT NULL UNIQUE | [backend/src/main/resources/db/migration/V37__purchase_payments.sql](../../../backend/src/main/resources/db/migration/V37__purchase_payments.sql) |
| payment_kind | VARCHAR(10) NOT NULL CHECK (payment_kind IN ('SUPPLIER','CARGO')) | [backend/src/main/resources/db/migration/V37__purchase_payments.sql](../../../backend/src/main/resources/db/migration/V37__purchase_payments.sql) |
| purchase_id | UUID NOT NULL REFERENCES purchases(id) | [backend/src/main/resources/db/migration/V37__purchase_payments.sql](../../../backend/src/main/resources/db/migration/V37__purchase_payments.sql) |
| reference | VARCHAR(200) | [backend/src/main/resources/db/migration/V37__purchase_payments.sql](../../../backend/src/main/resources/db/migration/V37__purchase_payments.sql) |
| request_fingerprint | VARCHAR(64) NOT NULL CHECK (request_fingerprint ~ '^[a-f0-9]{64}$') | [backend/src/main/resources/db/migration/V37__purchase_payments.sql](../../../backend/src/main/resources/db/migration/V37__purchase_payments.sql) |

### purchase_receipt_items

| Column | Last recognized declaration | Source |
|---|---|---|
| allocated_cargo_cost | NUMERIC(19,2) NOT NULL CHECK (allocated_cargo_cost >= 0) | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| purchase_id | UUID NOT NULL | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| purchase_item_id | UUID NOT NULL | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| quantity | BIGINT NOT NULL CHECK (quantity > 0) | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| receipt_id | UUID NOT NULL | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| total_cost | NUMERIC(38,2) NOT NULL CHECK (total_cost >= 0) | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |

### purchase_receipts

| Column | Last recognized declaration | Source |
|---|---|---|
| comment | VARCHAR(4000) | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| created_by | VARCHAR(200) NOT NULL | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| idempotency_key | UUID NOT NULL | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| posted_at | TIMESTAMPTZ NOT NULL | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| purchase_id | UUID NOT NULL REFERENCES purchases(id) | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| request_fingerprint | CHAR(64) NOT NULL | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| warehouse_id | UUID NOT NULL REFERENCES warehouses(id) | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |

### purchases

| Column | Last recognized declaration | Source |
|---|---|---|
| cargo_allocation_method | VARCHAR(20) NOT NULL CHECK (cargo_allocation_method = 'BY_QUANTITY') | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| cargo_cost | NUMERIC(19,2) CHECK (cargo_cost >= 0) | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| comment | VARCHAR(4000) | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| confirmed_at | TIMESTAMPTZ | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| costs_locked_at | TIMESTAMPTZ | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| created_at | TIMESTAMPTZ NOT NULL | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| created_by | VARCHAR(200) NOT NULL | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| status | VARCHAR(30) NOT NULL CHECK (status IN ('DRAFT','CONFIRMED','PARTIALLY_RECEIVED','RECEIVED','CANCELLED')) | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| supplier_id | UUID NOT NULL REFERENCES suppliers(id) | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| updated_at | TIMESTAMPTZ NOT NULL | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |

### refunds

| Column | Last recognized declaration | Source |
|---|---|---|
| amount | numeric(19, 2) not null | [backend/src/main/resources/db/migration/V15__create_refunds.sql](../../../backend/src/main/resources/db/migration/V15__create_refunds.sql) |
| comment | varchar(1000) | [backend/src/main/resources/db/migration/V15__create_refunds.sql](../../../backend/src/main/resources/db/migration/V15__create_refunds.sql) |
| id | uuid primary key | [backend/src/main/resources/db/migration/V15__create_refunds.sql](../../../backend/src/main/resources/db/migration/V15__create_refunds.sql) |
| idempotency_key | uuid not null | [backend/src/main/resources/db/migration/V15__create_refunds.sql](../../../backend/src/main/resources/db/migration/V15__create_refunds.sql) |
| method | varchar(30) not null | [backend/src/main/resources/db/migration/V15__create_refunds.sql](../../../backend/src/main/resources/db/migration/V15__create_refunds.sql) |
| reason | varchar(500) not null | [backend/src/main/resources/db/migration/V15__create_refunds.sql](../../../backend/src/main/resources/db/migration/V15__create_refunds.sql) |
| reference | varchar(200) | [backend/src/main/resources/db/migration/V15__create_refunds.sql](../../../backend/src/main/resources/db/migration/V15__create_refunds.sql) |
| refunded_at | timestamptz not null | [backend/src/main/resources/db/migration/V15__create_refunds.sql](../../../backend/src/main/resources/db/migration/V15__create_refunds.sql) |
| refunded_by | varchar(200) not null | [backend/src/main/resources/db/migration/V15__create_refunds.sql](../../../backend/src/main/resources/db/migration/V15__create_refunds.sql) |
| request_fingerprint | varchar(64) not null | [backend/src/main/resources/db/migration/V15__create_refunds.sql](../../../backend/src/main/resources/db/migration/V15__create_refunds.sql) |
| return_id | uuid | [backend/src/main/resources/db/migration/V15__create_refunds.sql](../../../backend/src/main/resources/db/migration/V15__create_refunds.sql) |
| sale_id | uuid not null | [backend/src/main/resources/db/migration/V15__create_refunds.sql](../../../backend/src/main/resources/db/migration/V15__create_refunds.sql) |

### return_items

| Column | Last recognized declaration | Source |
|---|---|---|
| condition | varchar(30) | [backend/src/main/resources/db/migration/V14__harden_returns.sql](../../../backend/src/main/resources/db/migration/V14__harden_returns.sql) |
| id | uuid primary key | [backend/src/main/resources/db/migration/V13__create_returns.sql](../../../backend/src/main/resources/db/migration/V13__create_returns.sql) |
| quantity | bigint not null | [backend/src/main/resources/db/migration/V13__create_returns.sql](../../../backend/src/main/resources/db/migration/V13__create_returns.sql) |
| return_id | uuid not null | [backend/src/main/resources/db/migration/V13__create_returns.sql](../../../backend/src/main/resources/db/migration/V13__create_returns.sql) |
| sale_item_id | uuid not null | [backend/src/main/resources/db/migration/V13__create_returns.sql](../../../backend/src/main/resources/db/migration/V13__create_returns.sql) |

### returns

| Column | Last recognized declaration | Source |
|---|---|---|
| comment | varchar(1000) | [backend/src/main/resources/db/migration/V13__create_returns.sql](../../../backend/src/main/resources/db/migration/V13__create_returns.sql) |
| created_at | timestamptz not null | [backend/src/main/resources/db/migration/V13__create_returns.sql](../../../backend/src/main/resources/db/migration/V13__create_returns.sql) |
| created_by | varchar(200) not null | [backend/src/main/resources/db/migration/V13__create_returns.sql](../../../backend/src/main/resources/db/migration/V13__create_returns.sql) |
| id | uuid primary key | [backend/src/main/resources/db/migration/V13__create_returns.sql](../../../backend/src/main/resources/db/migration/V13__create_returns.sql) |
| idempotency_key | uuid, | [backend/src/main/resources/db/migration/V14__harden_returns.sql](../../../backend/src/main/resources/db/migration/V14__harden_returns.sql) |
| reason | varchar(500) not null | [backend/src/main/resources/db/migration/V13__create_returns.sql](../../../backend/src/main/resources/db/migration/V13__create_returns.sql) |
| sale_id | uuid not null | [backend/src/main/resources/db/migration/V13__create_returns.sql](../../../backend/src/main/resources/db/migration/V13__create_returns.sql) |
| warehouse_id | uuid not null | [backend/src/main/resources/db/migration/V13__create_returns.sql](../../../backend/src/main/resources/db/migration/V13__create_returns.sql) |

### sale_comments

| Column | Last recognized declaration | Source |
|---|---|---|
| author | VARCHAR(200) NOT NULL CHECK (length(btrim(author)) > 0) | [backend/src/main/resources/db/migration/V55__sale_employee_comments.sql](../../../backend/src/main/resources/db/migration/V55__sale_employee_comments.sql) |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp() | [backend/src/main/resources/db/migration/V55__sale_employee_comments.sql](../../../backend/src/main/resources/db/migration/V55__sale_employee_comments.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V55__sale_employee_comments.sql](../../../backend/src/main/resources/db/migration/V55__sale_employee_comments.sql) |
| idempotency_key | UUID NOT NULL UNIQUE | [backend/src/main/resources/db/migration/V55__sale_employee_comments.sql](../../../backend/src/main/resources/db/migration/V55__sale_employee_comments.sql) |
| sale_id | UUID NOT NULL REFERENCES sales(id) | [backend/src/main/resources/db/migration/V55__sale_employee_comments.sql](../../../backend/src/main/resources/db/migration/V55__sale_employee_comments.sql) |
| text | VARCHAR(2000) NOT NULL CHECK (length(btrim(text)) > 0) | [backend/src/main/resources/db/migration/V55__sale_employee_comments.sql](../../../backend/src/main/resources/db/migration/V55__sale_employee_comments.sql) |

### sale_delivery_requests

| Column | Last recognized declaration | Source |
|---|---|---|
| created_at | TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp() | [backend/src/main/resources/db/migration/V57__sale_delivery_creation_idempotency.sql](../../../backend/src/main/resources/db/migration/V57__sale_delivery_creation_idempotency.sql) |
| generated_customer_id | UUID REFERENCES customers(id) | [backend/src/main/resources/db/migration/V1004__sale_delivery_generated_customer.sql](../../../backend/src/main/resources/db/migration/V1004__sale_delivery_generated_customer.sql) |
| idempotency_key | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V57__sale_delivery_creation_idempotency.sql](../../../backend/src/main/resources/db/migration/V57__sale_delivery_creation_idempotency.sql) |
| request_hash | VARCHAR(64) NOT NULL CHECK (request_hash ~ '^[a-f0-9]{64}$') | [backend/src/main/resources/db/migration/V57__sale_delivery_creation_idempotency.sql](../../../backend/src/main/resources/db/migration/V57__sale_delivery_creation_idempotency.sql) |

### sale_items

| Column | Last recognized declaration | Source |
|---|---|---|
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V8__create_sales.sql](../../../backend/src/main/resources/db/migration/V8__create_sales.sql) |
| product_variant_id | UUID NOT NULL | [backend/src/main/resources/db/migration/V8__create_sales.sql](../../../backend/src/main/resources/db/migration/V8__create_sales.sql) |
| quantity | BIGINT NOT NULL | [backend/src/main/resources/db/migration/V8__create_sales.sql](../../../backend/src/main/resources/db/migration/V8__create_sales.sql) |
| sale_id | UUID NOT NULL | [backend/src/main/resources/db/migration/V8__create_sales.sql](../../../backend/src/main/resources/db/migration/V8__create_sales.sql) |
| unit_sale_price | NUMERIC(19, 2) NOT NULL | [backend/src/main/resources/db/migration/V8__create_sales.sql](../../../backend/src/main/resources/db/migration/V8__create_sales.sql) |
| warehouse_id | UUID NOT NULL | [backend/src/main/resources/db/migration/V8__create_sales.sql](../../../backend/src/main/resources/db/migration/V8__create_sales.sql) |

### sales

| Column | Last recognized declaration | Source |
|---|---|---|
| cancellation_reason | VARCHAR(40), | [backend/src/main/resources/db/migration/V1006__sale_cancel_reason.sql](../../../backend/src/main/resources/db/migration/V1006__sale_cancel_reason.sql) |
| cancelled_at | TIMESTAMPTZ | [backend/src/main/resources/db/migration/V8__create_sales.sql](../../../backend/src/main/resources/db/migration/V8__create_sales.sql) |
| cancelled_by | VARCHAR(200) | [backend/src/main/resources/db/migration/V8__create_sales.sql](../../../backend/src/main/resources/db/migration/V8__create_sales.sql) |
| comment | VARCHAR(2000) | [backend/src/main/resources/db/migration/V49__p22_draft_sales.sql](../../../backend/src/main/resources/db/migration/V49__p22_draft_sales.sql) |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp() | [backend/src/main/resources/db/migration/V8__create_sales.sql](../../../backend/src/main/resources/db/migration/V8__create_sales.sql) |
| created_by | VARCHAR(200) NOT NULL | [backend/src/main/resources/db/migration/V8__create_sales.sql](../../../backend/src/main/resources/db/migration/V8__create_sales.sql) |
| customer_id | UUID | [backend/src/main/resources/db/migration/V8__create_sales.sql](../../../backend/src/main/resources/db/migration/V8__create_sales.sql) |
| fulfilled_at | TIMESTAMPTZ | [backend/src/main/resources/db/migration/V8__create_sales.sql](../../../backend/src/main/resources/db/migration/V8__create_sales.sql) |
| fulfilled_by | VARCHAR(200) | [backend/src/main/resources/db/migration/V8__create_sales.sql](../../../backend/src/main/resources/db/migration/V8__create_sales.sql) |
| fulfillment_type | VARCHAR(30) NOT NULL | [backend/src/main/resources/db/migration/V8__create_sales.sql](../../../backend/src/main/resources/db/migration/V8__create_sales.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V8__create_sales.sql](../../../backend/src/main/resources/db/migration/V8__create_sales.sql) |
| idempotency_key | UUID NOT NULL, | [backend/src/main/resources/db/migration/V9__add_sale_idempotency.sql](../../../backend/src/main/resources/db/migration/V9__add_sale_idempotency.sql) |
| sale_number | VARCHAR(30) NOT NULL UNIQUE | [backend/src/main/resources/db/migration/V8__create_sales.sql](../../../backend/src/main/resources/db/migration/V8__create_sales.sql) |
| status | VARCHAR(30) NOT NULL | [backend/src/main/resources/db/migration/V8__create_sales.sql](../../../backend/src/main/resources/db/migration/V8__create_sales.sql) |

### security_audit_log

| Column | Last recognized declaration | Source |
|---|---|---|
| action | VARCHAR(100) NOT NULL | [backend/src/main/resources/db/migration/V28__create_security_rbac.sql](../../../backend/src/main/resources/db/migration/V28__create_security_rbac.sql) |
| actor_user_id | UUID REFERENCES app_users(id) | [backend/src/main/resources/db/migration/V28__create_security_rbac.sql](../../../backend/src/main/resources/db/migration/V28__create_security_rbac.sql) |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT NOW() | [backend/src/main/resources/db/migration/V28__create_security_rbac.sql](../../../backend/src/main/resources/db/migration/V28__create_security_rbac.sql) |
| details | TEXT | [backend/src/main/resources/db/migration/V28__create_security_rbac.sql](../../../backend/src/main/resources/db/migration/V28__create_security_rbac.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V28__create_security_rbac.sql](../../../backend/src/main/resources/db/migration/V28__create_security_rbac.sql) |
| target_id | UUID | [backend/src/main/resources/db/migration/V28__create_security_rbac.sql](../../../backend/src/main/resources/db/migration/V28__create_security_rbac.sql) |
| target_type | VARCHAR(100) NOT NULL | [backend/src/main/resources/db/migration/V28__create_security_rbac.sql](../../../backend/src/main/resources/db/migration/V28__create_security_rbac.sql) |

### security_bootstrap_state

| Column | Last recognized declaration | Source |
|---|---|---|
| id | SMALLINT PRIMARY KEY CHECK (id = 1) | [backend/src/main/resources/db/migration/V31__admin_bootstrap_state.sql](../../../backend/src/main/resources/db/migration/V31__admin_bootstrap_state.sql) |
| initialized | BOOLEAN NOT NULL DEFAULT FALSE | [backend/src/main/resources/db/migration/V31__admin_bootstrap_state.sql](../../../backend/src/main/resources/db/migration/V31__admin_bootstrap_state.sql) |
| initialized_at | TIMESTAMPTZ | [backend/src/main/resources/db/migration/V31__admin_bootstrap_state.sql](../../../backend/src/main/resources/db/migration/V31__admin_bootstrap_state.sql) |

### security_permissions

| Column | Last recognized declaration | Source |
|---|---|---|
| code | VARCHAR(100) PRIMARY KEY | [backend/src/main/resources/db/migration/V28__create_security_rbac.sql](../../../backend/src/main/resources/db/migration/V28__create_security_rbac.sql) |
| description | VARCHAR(255) NOT NULL | [backend/src/main/resources/db/migration/V28__create_security_rbac.sql](../../../backend/src/main/resources/db/migration/V28__create_security_rbac.sql) |

### security_role_permissions

| Column | Last recognized declaration | Source |
|---|---|---|
| permission_code | VARCHAR(100) NOT NULL REFERENCES security_permissions(code) | [backend/src/main/resources/db/migration/V28__create_security_rbac.sql](../../../backend/src/main/resources/db/migration/V28__create_security_rbac.sql) |
| role_id | UUID NOT NULL REFERENCES security_roles(id) | [backend/src/main/resources/db/migration/V28__create_security_rbac.sql](../../../backend/src/main/resources/db/migration/V28__create_security_rbac.sql) |

### security_roles

| Column | Last recognized declaration | Source |
|---|---|---|
| code | VARCHAR(100) NOT NULL UNIQUE | [backend/src/main/resources/db/migration/V28__create_security_rbac.sql](../../../backend/src/main/resources/db/migration/V28__create_security_rbac.sql) |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT NOW() | [backend/src/main/resources/db/migration/V28__create_security_rbac.sql](../../../backend/src/main/resources/db/migration/V28__create_security_rbac.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V28__create_security_rbac.sql](../../../backend/src/main/resources/db/migration/V28__create_security_rbac.sql) |
| name | VARCHAR(200) NOT NULL | [backend/src/main/resources/db/migration/V28__create_security_rbac.sql](../../../backend/src/main/resources/db/migration/V28__create_security_rbac.sql) |
| system_role | BOOLEAN NOT NULL DEFAULT FALSE | [backend/src/main/resources/db/migration/V28__create_security_rbac.sql](../../../backend/src/main/resources/db/migration/V28__create_security_rbac.sql) |
| version | BIGINT NOT NULL DEFAULT 0 | [backend/src/main/resources/db/migration/V51__p23_roles_and_sales_drafts.sql](../../../backend/src/main/resources/db/migration/V51__p23_roles_and_sales_drafts.sql) |

### security_user_roles

| Column | Last recognized declaration | Source |
|---|---|---|
| role_id | UUID NOT NULL REFERENCES security_roles(id) | [backend/src/main/resources/db/migration/V28__create_security_rbac.sql](../../../backend/src/main/resources/db/migration/V28__create_security_rbac.sql) |
| user_id | UUID NOT NULL REFERENCES app_users(id) | [backend/src/main/resources/db/migration/V28__create_security_rbac.sql](../../../backend/src/main/resources/db/migration/V28__create_security_rbac.sql) |

### stock_movements

| Column | Last recognized declaration | Source |
|---|---|---|
| actor | VARCHAR(200) | [backend/src/main/resources/db/migration/V3__create_inventory.sql](../../../backend/src/main/resources/db/migration/V3__create_inventory.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V3__create_inventory.sql](../../../backend/src/main/resources/db/migration/V3__create_inventory.sql) |
| movement_type | VARCHAR(30) NOT NULL CHECK (movement_type IN ( 'PURCHASE_IN', 'SALE_OUT', 'RETURN_IN', 'TRANSFER_OUT', 'TRANSFER_IN', 'WRITE_OFF', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT' )) | [backend/src/main/resources/db/migration/V3__create_inventory.sql](../../../backend/src/main/resources/db/migration/V3__create_inventory.sql) |
| occurred_at | TIMESTAMPTZ NOT NULL | [backend/src/main/resources/db/migration/V3__create_inventory.sql](../../../backend/src/main/resources/db/migration/V3__create_inventory.sql) |
| operation_id | UUID NOT NULL UNIQUE | [backend/src/main/resources/db/migration/V3__create_inventory.sql](../../../backend/src/main/resources/db/migration/V3__create_inventory.sql) |
| product_variant_id | UUID NOT NULL | [backend/src/main/resources/db/migration/V3__create_inventory.sql](../../../backend/src/main/resources/db/migration/V3__create_inventory.sql) |
| quantity | BIGINT NOT NULL CHECK (quantity > 0) | [backend/src/main/resources/db/migration/V3__create_inventory.sql](../../../backend/src/main/resources/db/migration/V3__create_inventory.sql) |
| source_id | UUID NOT NULL | [backend/src/main/resources/db/migration/V3__create_inventory.sql](../../../backend/src/main/resources/db/migration/V3__create_inventory.sql) |
| source_type | VARCHAR(50) NOT NULL CHECK (source_type ~ '^[A-Z][A-Z0-9_]{0,49}$') | [backend/src/main/resources/db/migration/V3__create_inventory.sql](../../../backend/src/main/resources/db/migration/V3__create_inventory.sql) |
| warehouse_id | UUID NOT NULL | [backend/src/main/resources/db/migration/V3__create_inventory.sql](../../../backend/src/main/resources/db/migration/V3__create_inventory.sql) |

### suppliers

| Column | Last recognized declaration | Source |
|---|---|---|
| active | BOOLEAN NOT NULL | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| comment | VARCHAR(4000) | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| contact_information | VARCHAR(1000) | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| created_at | TIMESTAMPTZ NOT NULL | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| name | VARCHAR(200) NOT NULL CHECK (length(btrim(name)) > 0) | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| updated_at | TIMESTAMPTZ NOT NULL | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |
| version | BIGINT NOT NULL DEFAULT 0 | [backend/src/main/resources/db/migration/V4__create_purchasing.sql](../../../backend/src/main/resources/db/migration/V4__create_purchasing.sql) |

### warehouses

| Column | Last recognized declaration | Source |
|---|---|---|
| active | BOOLEAN NOT NULL | [backend/src/main/resources/db/migration/V2__create_warehouses.sql](../../../backend/src/main/resources/db/migration/V2__create_warehouses.sql) |
| address | VARCHAR(1000) | [backend/src/main/resources/db/migration/V2__create_warehouses.sql](../../../backend/src/main/resources/db/migration/V2__create_warehouses.sql) |
| code | VARCHAR(50) NOT NULL | [backend/src/main/resources/db/migration/V2__create_warehouses.sql](../../../backend/src/main/resources/db/migration/V2__create_warehouses.sql) |
| created_at | TIMESTAMPTZ NOT NULL | [backend/src/main/resources/db/migration/V2__create_warehouses.sql](../../../backend/src/main/resources/db/migration/V2__create_warehouses.sql) |
| id | UUID PRIMARY KEY | [backend/src/main/resources/db/migration/V2__create_warehouses.sql](../../../backend/src/main/resources/db/migration/V2__create_warehouses.sql) |
| name | VARCHAR(200) NOT NULL CHECK (length(btrim(name)) > 0) | [backend/src/main/resources/db/migration/V2__create_warehouses.sql](../../../backend/src/main/resources/db/migration/V2__create_warehouses.sql) |
| updated_at | TIMESTAMPTZ NOT NULL | [backend/src/main/resources/db/migration/V2__create_warehouses.sql](../../../backend/src/main/resources/db/migration/V2__create_warehouses.sql) |
| version | BIGINT NOT NULL DEFAULT 0 | [backend/src/main/resources/db/migration/V2__create_warehouses.sql](../../../backend/src/main/resources/db/migration/V2__create_warehouses.sql) |

