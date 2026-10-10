# ChairX P21-J — Backend financial closing audit

Date: 2026-10-10  
Branch: `feat/p21-daily-closing-v2`  
Scope: P21 daily closing, cross-module finance mutations and historical corrections. This is a **review and readiness checklist**, not a claim of production certification.

## Test evidence and status

The developer reported `mvn clean verify` **BUILD SUCCESS** after P21's AssertJ comparison fix. The prior run showed 616 tests with one BigDecimal-scale assertion failure, and an earlier run showed 616 tests with 17 failures that were subsequently fixed. These reports are user-provided. This assistant cannot execute Java 25 + Docker/PostgreSQL Testcontainers in its own runtime.

**IMPORTANT:** The audit below includes additional code/test commits AFTER the successful test run. A clean `mvn clean verify` must therefore be repeated; P21-J is not finally accepted until that run succeeds.

## Examined and confirmed from the repository

1. **Access policy:** `DailyClosingAccessPolicy` uses Asia/Bishkek. Ordinary editing is allowed for the report day and until **12:59:59** the following day; manager unlock eligibility ends at the start of day six; administrative unlocks can apply to older dates. Grants last at most 60 minutes.
2. **RBAC:** `SecurityConfig` has explicit HTTP-method/route permission checks and `anyRequest().denyAll()`. Expense corrections need both correction permission and date-write permission, plus the existing date window/grant. Basic Auth and CSRF are preserved.
3. **Serialization:** account balances use `SELECT ... FOR UPDATE`, with global BANK→CASH ordering for closing, transfers and historic corrections. Date-specific rows serialize grant/report writes and optimistic `version` prevents lost updates.
4. **Accounting:** financial account balance mutations go through `FinanceAccountRepository.changeBalance`, which enforces nonnegative balances and guards the currently closed day. Document posting, journal recording and historic closing revisions participate in the caller's transaction.
5. **Historic replay:** movement `business_date` is separate from immutable `created_at`; legacy dates are marked `LEGACY_INFERRED`. Reconstruction checks ledger/balance reconciliation and missing document postings; uncertain backdated legacy expenses are rejected pending manual reconciliation.
6. **Actual versus expected:** closing a report does **not** change CASH/BANK balances or auto-create expenses for differences. Differences require a note. An actual, idempotent late expense produces one dated journal debit and recalculates the affected report and subsequent closed reports. Original expected snapshots remain in `finance_daily_closing_accounts`, revised expectations in `finance_daily_closing_adjustments`; each changed report is versioned and audited with the source expense ID.
7. **Cross-module paths:** checked sales payments and reversals, purchase payments, refunds, and exchange settlements. Those service paths use the shared `FinancePostingService` instead of writing directly to the financial account balance.
8. **Flyway:** P21 added V41–V46 without rewriting existing applied migration scripts. V46 maintains the invariant that ADMIN has every seeded permission, while runtime unlock policy checks the more privileged ADMIN capability first.

## Concrete defect found and corrected during P21-J

**Two-leg transfer effective date:** `FinanceTransferService` previously passed an explicit Asia/Bishkek date for the debit, but used the implicit posting date for the credit. A midnight boundary could therefore leave a transfer's two legs attributed to different report days, even though the amounts sum to zero.

**Fix:**
- Add `FinanceMovementRepository.currentBusinessDate()`, reading the business date from the PostgreSQL clock in Asia/Bishkek.
- Both `FinanceTransferService` journal entries receive the *same* captured date and `POSTING_DATE` source.
- Derive implicit `finance_movements.created_at` and `business_date` from **the same PostgreSQL timestamp** using one CTE, instead of calling `clock_timestamp()` twice.
- Extend `FinanceTransferIntegrationTest` with checks for a single transfer business date across both legs, and consistency of implicit dates with recorded timestamps.

Only service/repository logic and tests were changed. No historical migrations or stored journal rows were rewritten.

## Known boundaries and follow-up

- Today's closing uses the currently locked account balances; unlike backdated closing, it does not invoke full per-account journal reconciliation. This preserves the existing backend contract, but a production reconciliation policy should explicitly decide whether to enforce a journal check before sealing the day. Do **not** silently add a strict check without making a migration/legacy-data plan and updating test fixtures.
- Corrected later reports can expose `requiresReview=true` for an altered expected amount. This flags work for reconciliation; it does **not** automatically approve a new discrepancy, change actual observations, or create a fake expense.
- Authorization expiry is rechecked inside the write transaction; there is no synchronous guarantee that an operation waiting outside the database transaction will be committed before the absolute grant expiry. Boundary and contention tests are present, but production monitoring remains advisable.
- The current audit examined cross-module financial write paths and P21 boundaries; it is not a complete application security penetration test or a production data migration dry-run.
- Baseline branch `main` is substantially behind the P21 feature (which also contains the audited backend integration work). Do **not** merge blindly; verify ancestry and planned integration target before a final PR.

## Remaining release gates

1. Pull the latest `feat/p21-daily-closing-v2` head and rerun `cd backend && mvn clean verify` on Java 25 with PostgreSQL 17 Testcontainers enabled.
2. Verify schema upgrades V40→V46 against a separate copy of an existing dataset and inspect legacy inferred journal dates. Back up before migrations.
3. Confirm no other feature branch adds overlapping Flyway version numbers before integration. Preserve the ordered migration history.
4. After a green build, create a reviewable PR to the agreed backend integration target. Keep `main` unchanged pending this acceptance. Resolve conflicts and rerun tests on the integration result.
5. If all gates succeed, finalize P21 as backend MVP complete and move to frontend, rather than adding unnecessary accounting modules.

## Key files

- `backend/src/main/java/kg/chairx/finance/application/DailyClosingService.java`
- `backend/src/main/java/kg/chairx/finance/application/DailyClosingAccessService.java`
- `backend/src/main/java/kg/chairx/finance/application/FinancePostingService.java`
- `backend/src/main/java/kg/chairx/finance/application/FinanceTransferService.java`
- `backend/src/main/java/kg/chairx/finance/persistence/FinanceAccountRepository.java`
- `backend/src/main/java/kg/chairx/finance/persistence/FinanceMovementRepository.java`
- `backend/src/main/java/kg/chairx/expense/application/ExpenseService.java`
- `backend/src/test/java/kg/chairx/finance/FinanceTransferIntegrationTest.java`
