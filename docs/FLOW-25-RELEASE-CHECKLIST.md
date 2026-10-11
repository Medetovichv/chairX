# FLOW-25 — main integration and release verification

## Mainline merge evidence

FLOW-01–FLOW-24 were merged sequentially through PR #49–#75. The mainline HEAD after integration was `d56f2196781eed456392c5710faedfe8be426edd`.

On that exact SHA, GitHub Actions **Backend verification** and **Frontend verification** reported success on the `push` event. These verify Java/Maven/PostgreSQL Testcontainers, and frontend lint/types/tests/build respectively.

The earlier FLOW-24 head also passed its seven CI checks, including F11 full-stack Chromium acceptance, **but a PR-head result does not prove F11 was executed on the merged main commit**. This PR adds an explicit `push: main` trigger for F11 when frontend, backend, or F11 scripts change, so future merges receive the full release gate.

## Verification checklist

- [x] Merge FLOW-01–FLOW-24 into main in dependency order.
- [x] Mainline backend verification green at `d56f219`.
- [x] Mainline frontend verification green at `d56f219`.
- [ ] Mainline full F11 PostgreSQL/Chromium gate green after the CI trigger update is merged.
- [ ] Browser/manual mobile UX acceptance on narrow real viewport/device.
- [ ] Review PR #47 demo-data isolation against main and explicitly choose merge/defer.
- [ ] Validate test fixtures never execute against a production database; verify backup/restore instructions and Docker persistent volumes.
- [ ] Run an operator smoke test for fresh deploy, upgrade and restart preserving records.

## Demo data

PR #47 is an independent Draft PR containing `docker-compose.demo.yml` and `docs/demo-data.md`. **It was deliberately not merged** into main because compatibility and isolation still require review. Do not use a demo configuration against production data.

## Release boundary

This work introduces no business functions, no third payment method, and no chair images. Keep financially destructive acceptance runs restricted to disposable CI databases. Do not mark production-ready based on a green unit suite alone.
