# ChairX — operator acceptance & evidence sheet (#96, #128)

**State: NOT EXECUTED / NOT SIGNED OFF.** This is a runnable checklist and record template, **not** proof that a human or representative business backup was tested.

## Scope and safety

- Git SHA under test: `fd6e6b67e9612161f50b9b3395aaf47640778d91` (replace with exact release HEAD at execution).
- Automated evidence at this SHA: [F11](https://github.com/Medetovichv/chairX/actions/runs/42323890093), [Backend verification](https://github.com/Medetovichv/chairX/actions/runs/42323890107): **success**.
- Use only an isolated staging installation with disposable **synthetic customer, stock and money data**. Do not point destructive workflows at production or a retained developer volume. No production credentials, bank details, real customer names or full backup archives in GitHub artifacts.
- For the representative backup recovery scenario, an authorized operator must provide an approved sanitized backup securely, outside GitHub. Its availability, provenance and authorization are **not established by this file**.
- Before signoff, record environment URL (no secrets), exact SHA, schema/Flyway version, reviewer identity/role, UTC and Asia/Bishkek dates, and fixture identifiers in a private evidence store.
- `PASS` requires both expected and actually observed outcomes plus independently retrievable evidence. A passing CI job is not a substitute for a human-observed flow.

## Execution matrix

| ID | Operator and fixture | Actions to execute in actual browser or approved isolated environment | Expected outcome / check | State |
|---|---|---|---|---|
| FIN-01 | Cashier + manager; **unpaid pickup** | Create 2-item pickup sale, confirm, cancel with fixed reason and optional comment, repeat request | Single cancellation, stock reservation released exactly once, reason/actor/time visible, no payment/finance movement | **PENDING** |
| FIN-02 | Cashier + manager; **paid pickup** | Pay once, attempt direct cancellation; follow allowed reversal/return workflow; retry with same operation identity | Direct unsafe cancel refused; original gross/payment preserved; compensation posted once on actual Bishkek date; net reflects refund | **PENDING** |
| FIN-03 | Cashier + driver + manager; **city delivery** | Create sale with address/phone, pay, dispatch, deliver, return one sellable item | Delivery state and recipient details correct; only returned quantity restocked; bank/cash journal and gross/refund/net reconcile | **PENDING** |
| FIN-04 | Cashier + driver + manager; **regional delivery** | Run unpaid and paid regional delivery; change statuses, verify reporting business date and payment restrictions | Unpaid delivery not treated as settled sale; paid sale counted on correct business/fulfilment date under agreed reporting rules | **PENDING** |
| FIN-05 | Manager; defect fixture | Return defective item from fulfilled sale, issue eligible refund, inspect warehouse blocked/defect stock | No false sellable stock credit, correct refund posting and source-document trace | **PENDING** |
| FIN-06 | Two concurrent employees | Attempt payment and cancellation simultaneously for same sale; repeat same operation and lost-response recovery | One authoritative outcome; no duplicate payments/refunds, movements, stock compensation or stale UI success | **PENDING** |
| FIN-07 | Manager/admin; two prior closed days | Close day with paid sales and discrepancy, post later authorized correction/refund, review report on both dates | Historical closing/snapshot unchanged; correction dated today, gross/refund/net reconciled with movements | **PENDING** |
| OPS-01 | Procurement + warehouse roles | Purchase/partially receive across 2 warehouses; complete, transfer, retry; inspect FIFO and zero-stock filters | Exact stock/reserved/on-hand and costs; no duplicate receipt/transfer or forbidden purchase-cost leak | **PENDING** |
| SEC-01 | ADMIN, MANAGER, EMPLOYEE and newly configured limited custom role | Try restricted finance/admin/purchase API actions; inspect raw 401/403/CSRF JSON, role changes and audit | Correct status and no private fields in forbidden responses; no unauthorized write; rights revoke immediately | **PENDING** |
| UI-01 | Cashier + independent UX reviewer | Open populated sales, delivery, inventory, return and finance at 320/375/768 and desktop; keyboard/tab/Escape, drawer focus, Back/Forward, filters, unsaved edit, failed request/retry | No unreachable control or overflow, expected focus behavior, filters and state restored, errors recover safely | **PENDING** |
| REC-01 | Authorized backup operator + reviewer | Obtain approved sanitized representative PGDMP, record checksum privately; restore to **new** PostgreSQL 17 instance, validate Flyway + app startup, compare row counts and finance snapshots | Unchanged original records, all applicable migrations validated, opening/closing balances equal journal and business summaries; app smoke successful | **PENDING** |

## Recording a result

Copy a row for **each** execution; never change the matrix to PASS from automated evidence alone:

```text
Case ID:
Executed by / second reviewer:
Run date/time / timezone:
Isolated environment + Git SHA + Flyway version:
Synthetic fixture IDs or secure backup evidence reference:
User role:
Viewport / browser / device (UI only):
Actions actually performed:
Expected behavior and exact numbers:
Observed behavior and exact numbers:
Evidence: private screenshot/video/log/SQL reconciliation reference (no secrets or personal data):
Result: PASS | FAIL | BLOCKED
Defect issue and steps to reproduce (if FAIL):
Signed off by:
```

## Independent financial reconciliation (FIN-02–07)

Before/after each action compare (a) the primary source order/payment/return/refund IDs and amounts, (b) signed `finance_movements` by **source ID** and account, (c) `finance_accounts` CASH/BANK balances, (d) the original frozen close and (e) reporting gross, refunds and net. Record actual amounts and explain any difference. Use the configured Asia/Bishkek operational date for new postings; never silently edit a closed historical snapshot.

## Recovery operator preconditions (REC-01)

The built-in `BackupUpgradeRehearsalTest.operatorProvidedBackup` is **skipped** unless `CHAIRX_REHEARSAL_DUMP` points to a readable local PostgreSQL custom-format archive. It restores into a newly started Testcontainers PostgreSQL 17 and checks Flyway plus original data fingerprints. Do **not** point it at production, do **not** upload the archive to CI/GitHub, and do **not** disclose credentials in logs. After successful automated restore comparison, an operator must separately start the app against the isolated restored DB, verify relevant UI/API read-only views and reconcile representative business totals. An operator-supplied backup is an explicit prerequisite; the synthetic V40/current CI jobs cannot satisfy it.

## Closure decision

- **#128:** all FIN rows signed, real operator roles and financial report reconciliation evidenced, outstanding defects closed; then close.
- **#96:** FIN + OPS + SEC + UI + REC rows signed, responsible business/technical reviewers accept, and final F11/Backend green **on the final intended release SHA**; then close.
- If any row is FAIL/BLOCKED, retain the Issue, create a specific defect or record the missing prerequisite; do not manufacture PASS evidence.
