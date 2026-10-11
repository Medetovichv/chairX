# ChairX F13 — Full System Integration & Production Readiness

> **Status: IN PROGRESS / NO-GO**. This is an incremental verification report,
> not permission to deploy. The F12 merge precondition has been verified.
> Never interpret all-green CI alone as safe production operations.

## 1. Baseline and architecture

- Baseline main after merged [F12 PR #35](https://github.com/Medetovichv/chairX/pull/35):
  `b3afbfa66c4c4ee50a097fba42443e0e621d752b`.
- Modular monolith, Java 25 / Spring Boot / Security / JDBC services,
  PostgreSQL 17, Flyway V1–V52, React/Vite/TypeScript/TanStack Query, Docker
  Compose. Backend enforces business rules; frontend checks are UX, not
  authorization. Single KGS currency.
- Backend tests cover domain transactions, concurrency, role lifecycle,
  real PostgreSQL/Testcontainers and historically corrected daily closing.
- Existing Playwright infrastructure is **already present** in
  `scripts/f11-browser-e2e.py` and `scripts/f11-operations-e2e.py`.
  F13 reuses that stack and runner, not a second browser framework.

## 2. Verified modules / acceptance matrix

| Area | Existing automated coverage | F13 gap / manual signoff |
| --- | --- | --- |
| Catalog, supplier, customer | Integration tests and populated REST/Chromium purchase chain | Full desktop/mobile click workflow pending |
| Purchasing and partial receiving | Authenticated API, real PG, two warehouses, replay and over-receipt guard | Cargo allocation variants, purchase-payment browser workflow |
| Stock / FIFO / transfers | Backend inventory/cost integration tests | F13 real API + PG transfer HOME→OFFICE→HOME, idempotent replay, historical list/detail cost-free JSON now passed; employee browser clicks and blocked inventory still pending |
| Sale/reservations | Backend, multi-warehouse sale API and Chromium detail | Parallel reservation last-chair UI, draft edit/cancel |
| Deliveries and returns | Dispatch/delivery, partial sellable return, refund and read-only SQL invariants | Failed city/region delivery UX, BLOCKED return end-to-end |
| Exchange and defects | Backend module integration tests | Full backend+UI purchase-to-exchange, defect parts/write-off scenario |
| Finance | Payment, refund, CASH/BANK balances, idempotency, purchase payment tests | Finance-transfer and mixed payment browser workflow |
| Daily Closing (F11) | Real Chromium + PG, multi-day HTTP+PG historical correction, optimistic version and grant expiry | Physical bank/cash reconciliation and accountant acceptance |
| Users, custom roles, RBAC (F12) | Merged integration tests + F13 new real React/PG/Chromium scenario | Role-by-role device testing with operational staff |
| Backups and Flyway | pg_dump -Fc / pg_restore / V40→V51 (11 migrations / 43 original tables) | Real authorized V40+ dataset and human reconciliation |
| React/Vite | Vitest, lint, TS and production build | Actual operator mobile/desktop observations |
| Docker | Compose smoke, clean disposable CI stacks | Real persistent volume restart and disaster-recovery drill |
| Security | Spring deny-by-default, Basic+CSRF, F12 admin-only role checks | HTTPS/TLS deployment plan, negative data-exposure tests |

**Important scope distinction:** Previous F11 “business operations E2E” sets up
master data and business transactions over *real authenticated HTTP* and observes
the sale, delivery and closing from Chromium. It does **not** click through each
purchase/return/exchange/defect screen. Do not mark those UI paths verified
until actual browser interactions are captured.

## 3. Findings, ordered by severity

| Priority | Finding | Evidence / action |
| --- | --- | --- |
| **RESOLVED — previously reported HIGH** | The transfer-cost disclosure described in #38 was fixed in main on 2026-10-10 by commit `6f38d113` (“fix(security): hide FIFO cost in inventory transfer APIs (#20)”). | Verified actual server DTOs, cost-free query SQL, POST controller and backend raw-JSON/CSRF/FIFO tests. [Issue #38](https://github.com/Medetovichv/chairX/issues/38) is **stale tracking**, not evidence of an active leak. Frontend comments in this F13 PR are updated to match. |
| **HIGH — blocks GO** | HTTPS, production credentials, disaster restore and real-data upgrade/operator approval are not proven. Existing local Compose is bound to localhost and uses development seed users. | No deployment authorized. See sections 7–8. |
| **MEDIUM — coverage** | Complete actual browser purchase/edit/reservation, exchanges, blocked return, defects and failed delivery not yet executed as a single real-browser suite. | Extend the existing F11/F13 Playwright infrastructure selectively where actual business risk warrants it. |
| **MEDIUM — acceptance** | Restricted roles, mobile widths and audit semantics must be validated with staff devices; CI screenshots are not operator acceptance. | Conduct role-by-role UAT. |
| **LOW / documentation** | `docs/architecture.md` predates F12 and includes an outdated note that P17 security integration is pending. | Update documentation only after relevant verified changes, not redesign runtime. |

No currently supported evidence warrants rewriting Java domain services,
adding Redis/Kafka/Kubernetes, or changing existing Flyway migrations.
Issues found in later test runs must be added here with actual proof and PR.

## 4. Real E2E scenarios and status

1. Previous F11 real Chromium: fresh postgres, CASH/BANK opening,
   preview-vs-posting race, close exactly once, frozen closing.
2. Previous Issue #32 populated flow: purchase, partial home/office receiving,
   sale, bank payment, deliver, partial refund, closing with real PG assertions.
3. **F13 passed in CI:** real browser creates an unprivileged employee;
   Admin API creates **test-only** warehouse/cashier roles and their permissions,
   assigns/revokes both roles; tests restricted actual API access, admin
   privilege injection denial, optimistic role version conflicts, permission
   revocation, password reset and old-password invalidation, employee
   deactivation/reactivation, audit redaction. Restricted employee authenticates
   to the real inventory page in mobile Chromium and is denied finance.
   Same isolated runner and backend as existing populated flow.
4. **F13 extended populated operations:** HOME→OFFICE→HOME inventory transfers through real authenticated HTTP after partial receipts, unchanged overall on-hand, duplicate transfer key/replay and conflict guards, 4 transfer stock movements, 2 FIFO origins, no cashier/bank journal effect, no cost fields in raw POST/list/detail JSON. The preexisting real sale/delivery/refund/closing flow still passes afterward.
5. **Not yet marked as complete:** full browser click paths for every workflow,
   chaos/network response-loss recovery, staff device/accessibility and
   human physical stock/cash signoff. Automated test results will be linked
   here after verification, not assumed.

## 5. Security and RBAC

- `SecurityConfig` uses HTTP Basic, authenticated `/api/auth/me` and
  `/api/csrf`, CSRF for unsafe writes and an explicit
  `anyRequest().denyAll()` route fallback. GET/POST role management
  demands both an active **system ADMIN** and its respective permission;
  user-visible role names never confer admin powers by themselves.
- Frontend auth is in memory, without persistent Basic credentials.
  Login/logout clear private Query cache and abort outstanding requests.
- F12 migrated V52 permissions for activation and password reset.
- **#38 was already resolved in main** by security fix #20. No new cost endpoint is needed; the backend continues to record FIFO valuations without exposing them to inventory readers.
- Production must terminate HTTPS, restrict DB access, rotate secrets and
  remove test/dev accounts. Current dev Compose is NOT production config.
- A browser test cannot replace code review of non-RBAC finance-sensitive
  response DTOs; run explicit limited-permission raw JSON checks.

## 6. Finance, warehouse and concurrency

Financial account rows and journal movements must reconcile, cash/bank posting
must be transactional, and dated closings immutable apart from separately
audited corrections. Existing integration tests cover races and retries.
Warehouse inventory remains `onHand - reserved - blocked`; FIFO allocations
are server-side and must not be returned to nonfinancial employees.
The new admin E2E does not create financial or stock movements, and it
asserts the existing populated F11 scenario still starts from zero.

## 7. Infrastructure, database and recovery

- `docker-compose.yml`: postgres:17, backend, frontend; local-only published
  ports, persistent named volume `chairx_postgres_data`, dependent service
  healthchecks and no external authentication proxy/HTTPS deployment.
- Existing `scripts/rehearse-db-upgrade.sh` supports
  **operator-approved private** `pg_dump -Fc` restoration to fresh temporary
  PostgreSQL 17, running Flyway and verifying original rows against SHA-256.
- Source data and real customer/employee secrets are NEVER attached to CI.
- **Pending**: operator runs local rehearsal with consented nonproduction
  historical backup, reconciles CASH/BANK and marked `LEGACY_INFERRED`
  business dates, and signs recovery time / restored account data.
- Never use `docker compose down -v` against a retained business database;
  that command appears only in guarded, disposable CI runners.

## 8. Go-live checklist (not automatically granted)

- [x] F12 merged into main.
- [x] Backend, frontend, Docker smoke and Flyway/PG Testcontainers CI exist.
- [x] Real Chromium + Spring + PG flow exists and uses synthetic data.
- [x] F13 real limited-role browser/admin lifecycle check passed in CI (synthetic data).
- [x] #38 transfer-cost bug already fixed in main by #20; raw-response and FIFO regression tests exist and pass.
- [ ] Full critical operational scenarios and retry/concurrency gates signed.
- [ ] Real authorized prior-business backup tested for restore and migration.
- [ ] Operator validates finance against CASH/BANK statements and stock counts.
- [ ] Mobile/tablet UX verified by representative staff.
- [ ] HTTPS, secure secrets, no seeded dev users, DB access and backup
      retention/recovery controls reviewed.
- [ ] No open deployment-blocking CRITICAL/HIGH issues.
- [ ] User explicitly approves production deployment as a separate task.

**F13 recommendation: NO-GO** until all blockers are resolved and evidenced.
This is an audit branch and PR: **do not merge F13 without user authorization**.

## 9. Run / restore commands

Development:
`docker compose up -d --build --wait`, then
`cd backend && mvn clean verify`; in frontend:
`npm ci && npm run lint && npm run typecheck && npm test && npm run build`.
For authorized backup instructions, see `docs/issue-32-acceptance.md`.
F13 E2E runs only inside the disposable GitHub runner before the existing
populated F11 business chain; never point the script at personal Compose data.

## 10. Evidence and final F13 disposition

- Baseline head: `b3afbfa` after F12 #35.
- Source audit: SecurityConfig, F12 controllers/services, route/auth session,
  daily closing, F11 scripts, Docker Compose, Flyway upgrade script.
- New F13 Chromium test: **PASS** on [latest F13 Actions run 38137153409](https://github.com/Medetovichv/chairX/actions/runs/38137153409). Browser admin creates an employee, backend assigns/revokes custom roles, reset invalidates old HTTP Basic, mobile warehouse renders with finance forbidden. The same runner subsequently completed stock transfers both directions and the purchase→delivery→refund→closing chain.
- All six F11 release gate jobs on the validated F13 code: **PASS** — Java 25/PostgreSQL Testcontainers full suite **676 tests, 0 failures, 0 errors, 1 skipped (operator-only private backup)**; Node 24 **355/355 frontend tests**, ESLint, typecheck and build; both Chromium E2Es, two-day financial correction and synthetic V40 PGDMP upgrade. Separate frontend verification and Docker Compose smoke: PASS. The document-only commits after this run do not change business or test code.
- Full signoff and final audit: **pending**. No production deployment or merge.
