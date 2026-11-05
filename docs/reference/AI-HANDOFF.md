# ChairX — reusable context for ChatGPT and other coding agents

Copy this instruction into the agent's first message **after giving it access to the repository at its current branch**:

> You are working on ChairX ERP/CRM. First read AGENTS.md and docs/reference/README.md. Inspect the current generated inventory (database.md, models.md, endpoints.md, services.md, frontend.md) and curated architecture-and-invariants.md. For any task, follow links to actual Java/SQL/TypeScript source and tests; never assume the generated indexes prove DTO contracts, final database state, runtime authorization or business correctness. Prefer SecurityConfig, DTOs, transactional services, Flyway migrations, and integration tests as sources of truth. If you change code, update the curated behavior notes if applicable, run python3 scripts/generate-codebase-reference.py and --check, and run relevant tests. Work in a PR; do not modify main or production data directly. Report the exact files modified, test evidence and unresolved risks.

## Practical navigation examples
- Changing a sale field? Search generated/models.md for Sale and SaleResponse; inspect the sale domain, request DTOs, repository SQL, all relevant migrations, services, and React form.
- Creating an API route? Inspect generated/endpoints.md then SecurityConfig and access tests. Verify method, URL, request, response and permissions from source.
- Changing finance? Inspect FinancePostingService, ledger persistence, cash/bank accounts, closings, migrations and Testcontainers tests.
- Changing stock? Inspect InventoryService and FIFO services, StockMovement, reservations, cost allocations and return/restock tests.
- Changing dashboard design? Read AppRouter.tsx, frontend feature directories, navigation.ts, actual API DTOs and UI tests.

## Freshness
The reference is versioned with source code. Generated/manifest.json contains a hash of indexed Java, SQL and frontend sources; the generator regenerates all indexes deterministically. The GitHub workflow checks/reconciles documentation drift. A hash is not proof that manual explanations are up to date; changes in business rules require review.

## Important boundaries
No secret credentials or private production records belong in the reference. Static indexes are intentionally readable without running a database; verify schema introspection separately against a disposable Flyway-migrated PostgreSQL instance when exact constraints/foreign keys are critical.
