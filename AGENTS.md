# ChairX: instructions for AI coding agents

Start with docs/reference/README.md. This file applies to the entire repository.

## Source of truth (in priority order)
1. Current Java controllers, DTOs, services, SecurityConfig, tests and Flyway migrations.
2. Current React routes, navigation guards, API clients and feature tests.
3. Generated inventory in docs/reference/generated/ (static navigation aids; not authoritative on complex SQL, JSON shapes or transactions).
4. Curated architecture and business context in docs/reference/architecture-and-invariants.md.
5. Historical docs/ documents; many have a frozen baseline (PR #51 / 2026-10-11) and must NOT override current code.

## Required workflow
- Read the generated indexes, then inspect the *actual* source files for affected code paths.
- Before changing persisted data, read all relevant Flyway migrations and repository queries. Never rewrite applied Flyway migrations or run destructive resets.
- For new or changed endpoints: update SecurityConfig, validation/DTOs, negative RBAC/CSRF tests and any relevant interface docs.
- For stock and money: preserve inventory reservations, FIFO restoration limits, atomic postings and closing invariants. Add real-PostgreSQL integration tests.
- For UI: check React permissions against backend; update tests for navigation, errors and pending operations.
- Run python3 scripts/generate-codebase-reference.py after changing Java, migrations or React. Commit generated docs with your changes.
- Run python3 scripts/generate-codebase-reference.py --check; then affected backend/frontend tests. Never declare a green build without actual CI evidence.
- Do not use historical Exchange workflows as current requirements: verify the current Return + new Sale model and remaining legacy compatibility tables.
- Add human-reviewed business explanations to the curated doc when behavior changes. Static extraction cannot infer intent.

## Code navigation
- Backend: backend/src/main/java/kg/chairx/<module>/{api,web,application,domain,persistence}
- Schema: backend/src/main/resources/db/migration/
- Security: backend/src/main/java/kg/chairx/config/SecurityConfig.java
- Frontend: frontend/src/features/, frontend/src/app/router/AppRouter.tsx
- Route permissions: frontend/src/shared/lib/navigation.ts
- API inventory: docs/reference/generated/endpoints.md
- Data model inventory: docs/reference/generated/database.md and models.md
- Service operations: docs/reference/generated/services.md
- UI overview: docs/reference/generated/frontend.md

## Documentation quality rules
- Never invent DTO fields, API parameters, database columns, permissions, or success claims.
- Label heuristically extracted facts as such; the scripts do not compile code or connect to a live database.
- Cite actual repository paths when describing behavior; prefer tests for behavioral guarantees.
- Keep deprecated workflows out of the current-facing summary unless explicitly documenting compatibility.
