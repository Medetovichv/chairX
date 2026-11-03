# ChairX Frontend Performance Audit — 2026-11-03

## Evidence and measurements

Production Vite builds (Node 24; minified assets reported by Vite) before and after route splitting:

| Measurement | Previous main (#149 F11 build) | Code-split candidate (#150 F11 build) | Change |
|---|---:|---:|---:|
| Initial JavaScript entry, minified | 1,088.86 kB | 905.87 kB | -182.99 kB (-16.8%) |
| Initial JavaScript entry, gzip | 287.49 kB | 242.48 kB | -45.01 kB (-15.7%) |
| Stylesheet, minified | 50.37 kB | 50.37 kB | unchanged |
| Stylesheet, gzip | 10.05 kB | 10.05 kB | unchanged |

The candidate split uses 16 statically analyzable React.lazy imports for finance, admin, and refund pages, retaining route permission gates and the common layout. Note: gzip totals above concern the **entry file only**, not all JS bytes eventually loaded on finance/admin routes. Chunk splitting trades lower initial download for additional on-demand requests. These are **build sizes**, not Lighthouse/Web Vitals, TTI, LCP, CLS or mobile field measurements.

## Skill guidance applied

- **Vercel React Best Practices:** `bundle-dynamic-imports`, `bundle-analyzable-paths`: route-level code splitting. `client-swr-dedup`: retain TanStack Query cache and existing query keys. Avoid hidden sales KPI requests while browsing history.
- **Impeccable (Operate):** preserve existing route behavior and visible loading feedback; avoid a blank canvas while a page chunk loads; do not alter finance copy or calculations.
- **UI UX Pro Max:** accessible loading state (`role=status`) and full keyboard/mobile browser regression.

## Ongoing guardrail

A Node built-in-only postbuild check reads `dist/index.html` to identify the startup entry and enforces <=950 kB minified and <=255 kB gzip. CI will reject size regressions; these budgets are deliberately above measured current output, not asserted universally optimal thresholds.

## Scope and limitations

- The initial bundle is **still above Vite's 500 kB warning**; optimize further only after measuring users' critical routes and respecting RBAC, browser cache and network variance.
- The complete Java backend/F11 real-browser suite remains the release gate. Router tests must await lazy page loading rather than relying on synchronous imports.
- React render counts, request waterfall, actual Lighthouse and Core Web Vitals require separate profiling under a representative authenticated production-like deployment; this PR does **not** claim those measurements.
