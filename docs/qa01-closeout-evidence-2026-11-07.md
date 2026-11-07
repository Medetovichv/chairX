# ChairX QA-01 / #128 — closeout evidence and blocking signoff (2026-11-07)

**Main application SHA:** `cd834ca222b75c3f03fec83cc68af506d7f9718e` (reconfirm before operator UAT).  
**Automated acceptance:** passed in the merged PR heads through #262; [merged-main F11 #42407198090](https://github.com/Medetovichv/chairX/actions/runs/42407198090) must be checked for its final result. **This report is not permission to release to production.**

## #128 — criterion-by-criterion result

| Acceptance criterion | Automated evidence | Business signoff |
|---|---|---|
| Fixed reason catalog; required reason, optional comment | `SaleCancellationReason`, authenticated `SaleController`; F11 invalid-reason rejection and selected reason | **PENDING** real cashier demonstration |
| Reason, actor, date and audit; RBAC on cancellation | F11 `GET /cancellation-details`, exactly-once audit, forbidden catalog role; #259 | **PENDING** role-based review |
| Unpaid DRAFT / CONFIRMED cancellation, pickup, two warehouses | F11 real POST /confirm → POST /cancel-with-reason, reserve release in HOME and OFFICE, no bank/stock postings; #258/#259 | **PENDING** employee walkthrough |
| Safe retry after lost response, isolation by actor | F11 idempotent operation key and receipt retrieval, mismatch key rejection, second employee 404; #260/#261 | **PENDING** simulated network disconnection in staffed staging |
| Paid/fulfilled direct cancellation refused; compensation via return/refund | Existing backend payment/refund regression; F11 paid city delivery cancelled via wrong flow forbidden, real fulfilled partial return/refund, exactly-once payment and refund | **PENDING** paid pickup and regional delivery walkthrough |
| Historical closure preserved; refund posting on actual date | `RefundApiTests` authorized close/unlock/return, financial immutability; #250/#258; F11 multi-date and closing | **PENDING** manager/finance reconciliation |
| Gross, returned and net revenue; bank ledger | #255 and #262 real authenticated report and ledger: **25,500 gross / 8,500 returned / 17,000 net**, bank **+25,500 / -8,500**, no double deduction | **PENDING** finance operator verifies representative order |

**Result for #128:** the specified automatic smoke/regression paths are demonstrated, but the original Issue explicitly demands real operators, stocked staging, two roles, region delivery, defective items and simultaneous user actions. Those have **not** been executed by this report. Issue remains OPEN until signed.

## #96 — system acceptance

| Module | Executable evidence | Missing release acceptance |
|---|---|---|
| Sales, pickup, cancellation, payments and returns | Full backend Testcontainers; F11 populated chain; #258–#262 | Actual operator sale / back-forward / network-failure walkthrough |
| Purchases and warehouses | F11 real purchases, two receipts and transfers; PostgreSQL warehouse/cost suites | Partial receipt / FIFO source trace manually reviewed by warehouse and finance |
| City/region delivery | F11 city delivery dispatch and completion; region state/backend regression | Paid/unpaid regional real-role workflow and today-delivered list |
| Returns, defects, Exchange removal | Full returning/refund tests; F11 linked partial return + refund | Defective physical return, blocked sellable stock, no retired Exchange creation in UI |
| Financial reconciliation | #262 user-facing P&L and ledger; F11 historical two-day tests | Representative business spending/withdrawal, documented gross/net reconciled |
| Day closing / authorization | F11 real browser closing and immutable snapshot; backend unlock/time-zone suites | Actual cashier → manager review; time around Asia/Bishkek cutoff |
| Security / privacy | #254 role checks; #261 independent employee result protection | Manager/custom role reviewed in real populated staging |
| UX 320/375/768 / a11y | Chromium responsive route and keyboard tests, saved CI screenshots | Human review on real devices with focus, unsaved form, filters and stress content |
| Backup restore and Flyway | #256 PG17 custom-format synthetic V40 and current-schema backups restored in fresh containers; F11 green | **Approved representative sanitized business backup** acquired securely, independently restored, opening/closing account reconciliation and operator app smoke |

**Result for #96:** synthetic release gate is not the requested full human acceptance. Issue remains OPEN.

## Remaining operator signoff — exactly 11 rows

The executable [operator checklist](qa01-operator-acceptance-checklist.md) defines FIN-01 … FIN-07, OPS-01, SEC-01, UI-01 and REC-01 with roles, step-by-step actions, expected values, evidence template and privacy restrictions.

Only after each row has dated **observed** results and evidence links can it be marked PASS. Any FAIL requires a defect Issue and correction with green PR CI. Any BLOCKED requires named missing preconditions, not an inferred PASS.

## Safe execution protocol

1. Authorized owner provisions an isolated staging deployment with a disposable, representative fixture. Record URL (no secrets), SHA, migration version, reviewer and business date in private evidence.
2. Cashier, warehouse, delivery, manager and finance reviewers execute the 11 scenarios; capture expected vs actual results and sanitized screen/video/log references privately.
3. A designated operator obtains an **authorized and sanitized** PostgreSQL custom-format backup *outside GitHub*, records checksum privately and runs `BackupUpgradeRehearsalTest.operatorProvidedBackup` with `CHAIRX_REHEARSAL_DUMP` against an **independent** PostgreSQL 17 container. This test is opt-in and skipped in CI without a supplied dump.
4. Start application against the **isolated restored DB** and reconcile customer/order/item counts, finance movements, balances and historical closings without mutating original records; sign result.
5. Check the final **main SHA** in F11/Backend after any fixes; record operator approval. Close #128 and #96 only if the relevant complete matrix passes.

**Never upload production customer data, credentials or full archives into Issues/CI artifacts. Never point destructive smoke tests at production.**
