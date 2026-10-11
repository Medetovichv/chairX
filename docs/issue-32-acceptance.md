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
