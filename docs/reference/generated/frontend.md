<!-- Auto-generated; do not edit. See scripts/generate-codebase-reference.py -->

# React/TypeScript frontend index

Routes are recognized statically. Actual gates, redirects, lazy loading and access checks must be inspected in AppRouter.tsx and navigation.ts. UI permissions do not replace backend security.

## Recognized routes

- /
- /admin
- /admin/audit
- /admin/roles
- /admin/users
- /catalog
- /catalog/:productId
- /customers
- /customers/:customerId
- /daily-closing
- /deliveries
- /deliveries/:deliveryId
- /deliveries/new
- /finance
- /finance/analytics
- /finance/cash-flow
- /finance/expenses
- /finance/obligations
- /finance/opening-balances
- /finance/operations
- /finance/report
- /finance/transfers
- /inventory
- /inventory/transfers
- /inventory/warehouses
- /login
- /purchases
- /purchases/:purchaseId
- /purchases/payments
- /purchases/receipts
- /purchases/suppliers
- /returns
- /returns/:returnId
- /returns/:returnId/refund
- /returns/defects
- /returns/defects/:defectId
- /returns/new
- /sales
- /sales/:saleId
- /sales/new

## Feature directories

| Feature | TS/TSX files |
|---|---:|
| admin | 11 |
| auth | 2 |
| catalog | 9 |
| customers | 11 |
| daily-closing | 10 |
| dashboard | 4 |
| deliveries | 17 |
| finance | 23 |
| inventory | 12 |
| purchases | 23 |
| returns | 17 |
| sales | 21 |
