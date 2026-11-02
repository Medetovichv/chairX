# Dashboard 2.0 — iteration 1

## Scope
This first change is a **visual foundation**, not a finished dashboard redesign. Based on the current `frontend/src/pages/HomePage.tsx` and the imported upstream UX guidance in draft PR #129.

- Soften the dashboard surface and use consistent rounded cards.
- Replace overly saturated blue accents with restrained emerald accents.
- Improve KPI hover/focus affordance and numeric typography.
- Harmonize chart stroke/fill with the same visual language.
- Keep data, API requests, query keys, role gates, navigation paths and business rules unchanged.

## Critical follow-up, not implemented here
**Do not describe current KPI links as filtered drilldowns.** They still navigate to general module routes. Before implementing exact drilldowns, inspect the actual sales, delivery and inventory list filter/query contracts, then synchronize URL state and list filters. Add tests verifying correct result sets, browser Back/Forward and authorization.

Additional follow-up: prototype an operations-focused manager home, enhance charts with accessible daily breakdown, explore delivery workboard and role-specific home layouts. Do not invent financial KPIs not delivered by backend.

## Verification
This commit was authored via the GitHub connector without a checked-out build environment. **Lint, typecheck, tests, build and visual browser review have not been run for this branch.** PR should remain draft until those checks are completed.
