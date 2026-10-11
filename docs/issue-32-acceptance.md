# Issue 32 — evidence-driven F11 release gate

Issue: https://github.com/Medetovichv/chairX/issues/32

## Automated CI coverage

A dedicated GitHub Actions workflow runs two independent jobs:

1. Java 25, PostgreSQL 17 Testcontainers and complete backend Maven verification. This re-runs existing operational P20, P21/P22 historic corrections/permissions/concurrency tests and the synthetic V40-to-current Flyway upgrade.
2. Actual headless Chromium against the running Vite frontend and Spring API through Docker Compose with a disposable PostgreSQL database. Checks real employee sign-in, scoped authorization, opening balance API, current expected balances, a concurrent bank-to-cash transfer during the confirmation dialog, refusal to post stale values, successful day closing, immutable snapshot marker, duplicate close rejection and current-day ledger lock. Inspects actual database rows after every financial step and uploads screenshots.

## Non-negotiable isolation

The browser test is **destructive** and refuses to run without CI=true, an explicit disposable environment flag, localhost API and an **empty finance database**. Do not invoke on your local ChairX Docker volume or production. GitHub CI generates new random credentials and cleans up its ephemeral Docker volumes after the run.

## Not proven by CI

Synthetic upgrade tests are not a migration rehearsal with a copy of existing business data. This automated browser scenario uses a closing with zero completed sales; the real business sale/delivery/returns workflow has dedicated PostgreSQL service-level integration tests, but no single real-browser purchase-to-refund journey is claimed here. A real-data migration rehearsal, comprehensive business-browser run, manual cash/bank reconciliation and human acceptance remain separate release gates. Keep Issue #32 open until these are explicitly done, even if automated CI turns green.


## Populated cross-module acceptance — added in subsequent PR

A separate disposable Compose/Chromium job executes one cohesive *real HTTP* business process end to end. It deliberately has a **different empty PostgreSQL volume** from the original empty-day closing test:

- Create supplier, product, variant, customer and opening balances through authenticated REST APIs.
- Attempt to sell without stock: business rejection, zero sales or ledger side effects.
- Purchase six chairs; receive four into HOME and two into OFFICE; repeat receipt with the **same idempotency key**; verify one receipt, correct stock and no invented payment movement.
- Sell three chairs across two warehouses for 25,500 som; record genuine MBANK payment; reject second active payment and assert no duplicate journal.
- Dispatch and deliver, verify completed sale and stock consumption; return one sellable chair to HOME and refund 8,500 som, twice with the exact same operation keys; assert only one return/refund movement and final CASH/BANK 20,000 / 117,000 som.
- Load real sale, delivery and populated daily closing in **Chromium**, confirm the financial day through the UI, and assert a saved immutable one-sale P22 snapshot, two closing account observations, unchanged finance ledger, and immutable today's close blocking further transfers.
- Catalog-only HTTP credentials cannot mutate logistics or see financial reports; every financial negative path asserts the absence of unintended writes.

**Precise scope:** API is used to perform procurement/warehouse/sale/delivery/refund actions; Chromium renders the **real stored** sale, delivery and closes the day interactively. This proves real browser presentation and API→database transactions, but does not assert every create/dispatch/refund button in the React UI. A fully click-driven sales-and-returns walk-through is still part of manual acceptance. Real-data migration and a multi-date historical correction scenario also remain distinct from this test.


## Historical two-day PostgreSQL acceptance (Issue #32)

A dedicated Java 25/Spring MockMvc/real PostgreSQL 17 Testcontainers gate
uses adjustable test time for **two authentic REST closing transactions** on
two consecutive Asia/Bishkek business dates. Neither business policy nor the
production server clock are replaced in deployable code.

Acceptance asserts:
- Correct first and second day close (normal business-day windows), with
  exactly two saved closing records and independently frozen sales snapshot
  markers. Closing/observation writes do not post fictitious finance money.
- Three days later, normal employee permission does not bypass historical
  locks; manager unlock is scoped to one specific date.
- One authorized, backdated cash expense produces **one** document, **one**
  journal movement and one real account delta, but recalculates expectations
  for *both* previously closed days atomically.
- Original expected balances remain in the original closing account rows;
  recalculated values are separate adjustments. First shortage is reconciled,
  while the later day explicitly displays \`requiresReview\` until a human
  enters a new observation; saved sales snapshots remain unchanged.
- Same-key expense replay produces zero further postings or revisions; use of
  the same key with a different amount is rejected.
- Manual report update requires a per-day unlock plus current version; stale
  version 409 causes zero state changes. A fresh counted observation resets
  \`requiresReview\` **without changing account money**.
- Audit exposes prior observation, initial close, recalculation and manual
  update; admin revocation blocks later financial correction, a 60-minute
  manager unlock expires exactly at the cutoff, manager historical age limit
  applies, and admin unlock/revoke remains independently auditable.

**Test-only time travel**: injected \`Clock\` in an isolated Testcontainers
Spring context; financial posting date still uses actual application guards.
This tests the cross-day API/domain invariants without touching a real retained
database. Browser presentation is exercised in the existing Chromium gates;
this new scenario uses MockMvc as the real HTTP layer rather than claiming a
multi-day Playwright GUI run. A rehearsal of migrations against a suitably
consented, sanitized old database and human UAT are still required.

## Issue #32 — safe PostgreSQL backup→Flyway rehearsal

**Local-only, authorized data.** A database archive can contain client phone
numbers, payment information, employees, passwords, roles and commercially
sensitive transactions. Never commit it, upload it to GitHub/ChatGPT or paste
its contents in an issue. Get data-owner authorization and keep it on an
approved encrypted local device **outside this Git repository**.

### 1. Make an original, read-only custom-format backup

From the ChairX project root, with your existing local Compose database
already running:

    umask 077
    mkdir -p "$HOME/chairx-private-backups"
    docker compose exec -T postgres pg_dump -U chairx -d chairx \
      -Fc --no-owner --no-acl \
      > "$HOME/chairx-private-backups/chairx-before-upgrade.dump"

The pg_dump command reads a transactionally consistent database snapshot.
It does not alter the source. You may instead use an authorized existing
-Fc archive. Do not collect a backup from an unapproved production system.

### 2. Rehearse against a fresh, isolated PostgreSQL 17 instance

    bash scripts/rehearse-db-upgrade.sh --acknowledge-sensitive-backup \
      "$HOME/chairx-private-backups/chairx-before-upgrade.dump"

Requires Java 25, Maven, Docker, and a PostgreSQL custom-format PGDMP
archive containing an existing ChairX public.flyway_schema_history at V40
or newer. The script refuses files inside Git and non-PGDMP input.
It never contacts the source database, runs the Compose stack, changes
the source volume or invokes Flyway.clean(). Testcontainers copies the
archive into a BRAND NEW disposable PostgreSQL instance, restores it
using pg_restore, and applies the checked-in Flyway migrations only there.

On success the output contains CHAIRX_UPGRADE_REHEARSAL=PASS and reports
the starting version, **new migrations executed**, original table count
and pre-existing movement count without listing business records.
If migrationsExecuted=0, the backup was already on the checked-out schema;
this is **not evidence** that an actual V40→latest upgrade succeeded.
A failure or drift requires investigation before deployment.

### Acceptance invariants

- All pre-existing application tables, from stock and purchasing to audit,
  customers, users, sales, CASH/BANK and closings, are compared using their
  **original columns**, row count, and a sorted canonical SHA-256 fingerprint.
  New columns and tables are permitted. A change to old values fails.
- Previously applied Flyway history, migration descriptions and checksums
  remain identical. New migrations must all validate.
- Only system permission and role grant seed tables may grow, and **every
  original permission/grant row** must remain unchanged.
- For a source predating V43: legacy journal movement dates must be inferred
  from the original created_at in Asia/Bishkek. The source label is
  LEGACY_INFERRED, **not proof** of the actual historical transaction date.
  Backdated imports need manual reconciliation.
- For a source predating V50: no old closing snapshot rows may be invented.
  For newer backups, genuine pre-existing snapshot rows are preserved.
- CASH/BANK balances must reconcile to the journal totals after upgrade.
  Discrepancies block a PASS, rather than silently manufacturing movements.
- The original restored data is never modified for synthetic migration tests.
  A rehearsal does not prove physical stock, bank statement agreement,
  real-world role suitability, or successful manual UI acceptance.

### What CI can and cannot prove

The release-gate CI runs the **same** PGDMP→pg_restore→Flyway→SHA-256
pipeline using a populated synthetic V40 dump, on independent disposable
PostgreSQL 17 containers. Its fixtures include balances and movements,
a historical closing, catalog variants, supplier, purchase and customer.

**CI cannot access a real ChairX backup.** A successful synthetic test
does not close the final *actual business-data* migration milestone.
After executing the operator command locally, record privately the
approved backup date, before/after migration versions, number of migrations
executed, any legacy-inferred-date concerns and financial reconciliation
approval. Never disclose the data or secrets in issue comments.
