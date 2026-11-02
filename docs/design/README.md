# ChairX Design 2.0 — design direction

## Goal
Make ChairX feel like a responsive, interactive operating workspace instead of a set of unrelated spreadsheets. Retain efficient dense tables where they help (stock reconciliation, finance ledger). Use cards, Kanban, actionable charts, drawers and schedules when they help employees act.

## Reference and sources
The product owner supplied CRM dashboard screenshots showing warm restrained styling, live-feeling KPI cards, charts, customer cards and finance drilldowns. These are inspiration, not a license to reproduce the artwork or a specification for backend behavior.

Open-source inspiration (not copied or installed here):
- UI UX Pro Max: https://github.com/nextlevelbuilder/ui-ux-pro-max-skill
- Impeccable: https://github.com/pbakaus/impeccable
- Anthropic frontend-design: https://github.com/anthropics/claude-plugins-official/tree/main/plugins/frontend-design
- Vercel agent-skills: https://github.com/vercel-labs/agent-skills

All added Skills are original ChairX-specific instructions. External skills have different packaging and runtime support; no scripts, packages, licensed text or automatic agents have been bundled. Inspect upstream licenses and files before installing any external tools.

## Phase 1: Home dashboard
- Review the current `frontend/src/pages/HomePage.tsx`, `features/dashboard/types.ts`, dashboard API and RBAC.
- Current overview includes order counts, delivery counts, stock levels, a 7-day order trend and recent sales; do not present revenue/margin as existing API data.
- Explore action center, clickable KPI → filtered detail, period selection with real data, relevant order cards and manager-only finance modules.
- Define data contracts for additional metrics before writing UI that needs them.
- Build prototype in isolated branch, validate with actual roles and realistic anonymized data.
- Test interactions, accessibility, 320px viewport, desktop and regression.
- Keep production logic and main untouched pending review.

## Follow-up
Apply a single visual system gradually to Sales, Deliveries, Customers, Inventory, Finance and Closing. Establish outcome-based acceptance criteria per page.
