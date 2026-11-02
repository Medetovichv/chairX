# ChairX project guidance

ChairX is a working ERP/CRM for office-chair retail, purchases, stock, sales, deliveries, returns and finance. Protect existing data and business processes.

## Before frontend work
1. Read `.agents/skills/chairx-ux-designer/SKILL.md` and the relevant specialist guide.
2. Inspect the current page, API contract, permissions, responsive behavior and tests.
3. Explain the user's primary job, propose suitable representations (table, cards, Kanban, timeline, calendar, dashboard) and choose based on the workflow—not aesthetics alone.
4. Prefer existing React/TypeScript/Tailwind/Radix/Lucide patterns. Do not add dependencies without evidence.
5. Prototype significant redesigns before replacing working screens.

## Safety & quality
- Do not fabricate KPI numbers; derive them from authorized backend data, with documented definitions and date/time zone (Asia/Bishkek).
- Preserve RBAC in the UI and enforce it server-side; never reveal unauthorized finance data.
- A dashboard metric links to a matching filtered detail view; filters must actually work.
- Preserve native links, keyboard navigation, visible focus, accessible names, responsive layouts and clear loading/empty/error states.
- Never alter closed financial periods, payments, returns, stock or delivery business rules as a side effect of a design change.
- Use a dedicated branch and PR; report what was tested and what was not. Do not merge without authorization.

## Specialized guides
- UX/product design: `.agents/skills/chairx-ux-designer/SKILL.md`
- Dashboard and data visualization: `.agents/skills/chairx-dashboard-design/SKILL.md`
- React implementation: `.agents/skills/chairx-frontend-engineering/SKILL.md`
- UX verification: `.agents/skills/chairx-ux-review/SKILL.md`

These are repository instructions that a ChatGPT GitHub workflow must read intentionally; they are not self-executing plugins.
