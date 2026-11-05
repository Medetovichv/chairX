# ChairX — AI-ready codebase reference

This folder is the entrypoint for ChatGPT, coding agents and human maintainers. It is **part of the repository**, not a standalone specification.

## Quick start for an AI agent
1. Read root [AGENTS.md](../../AGENTS.md), this README and [architecture and invariants](architecture-and-invariants.md).
2. Locate the relevant **current** modules with the generated indexes below.
3. Open the linked Java / SQL / TypeScript files; verify implementation and tests, especially before touching money, stock or permissions.
4. Apply changes in a feature branch. Regenerate all indexes, run tests and open a PR. Do not assume a previous chat or October 2026 docs match main.

[Reusable AI onboarding prompt](AI-HANDOFF.md) — copy into ChatGPT, Claude or another coding agent with repository access.

## Generated source inventories
| Resource | What it contains | Caveat |
|---|---|---|
| [Database](generated/database.md) | Flyway table/column snapshots and detected FK references | Static SQL heuristic, not an exact live schema |
| [Models](generated/models.md) | Java record components and enums | Not JSON schema or JPA metadata |
| [API endpoints](generated/endpoints.md) | Controller methods, HTTP verbs and paths | Not security/DTO/OpenAPI completeness |
| [Services](generated/services.md) | Application-layer public methods by module | Not call graph or contract |
| [Frontend](generated/frontend.md) | Routes and feature groups | Gates and dynamic routes require source inspection |
| [Manifest](generated/manifest.json) | Hash of indexed sources and inventory statistics | For reproducibility, not release certification |

## Curated complementary docs
- [Architecture, important entities and business invariants](architecture-and-invariants.md) — reviewed guidance; must be updated when business behavior changes.
- [Existing technical reference](../technical-reference.md) — historical baseline after PR #51, not current truth.
- [Existing API policy matrix](../http-access-matrix.md) — historical baseline, verify current SecurityConfig.
- [Business rules](../business-rules.md) — historical and later additions, cross-check current tests.
- [Production readiness](../f13-production-readiness.md) — historical acceptance report, not a current GO decision.

## Update and CI contract
Run locally:

    python3 scripts/generate-codebase-reference.py
    python3 scripts/generate-codebase-reference.py --check

The generator has **no external Python dependencies** and writes deterministic UTF-8 Markdown/JSON. Commit changed generated files in the same PR as code changes. The AI reference workflow regenerates on same-repo PRs, and on main/scheduled runs proposes a documentation-only PR if drift appears. It does not deploy or mutate customer data.

For API changes, generated endpoints are only a discoverability layer. Always consult real controller request/response DTOs, SecurityConfig and tests. For schema changes, verify against migrated PostgreSQL; the extractor handles simple CREATE / ALTER additions and removals but not all SQL forms. For transaction-sensitive behavior, document it manually and cover with integration tests.

### Architecture boundaries
Browser (React + TypeScript) -> /api -> Spring Boot services (Java) -> Spring JDBC/JdbcClient -> PostgreSQL 17 / Flyway. Authentication, CSRF, RBAC and validation are enforced server-side. Finance journal and inventory movements have transactional side effects. KGS and Asia/Bishkek date boundaries matter.

### What is intentionally *not* claimed
These indexes do **not** prove all routes are reachable, all DTOs serialized as shown, all SQL constraints captured, production readiness, complete automated test coverage, or correctness of business logic. Use actual running tests and database inspection for those conclusions.
