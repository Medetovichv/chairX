---
name: chairx-frontend-engineering
description: Implement maintainable, accessible React and TypeScript CRM interactions in ChairX.
---
# ChairX Frontend Engineering

## Stack
Read `frontend/package.json` before modifying dependencies. Currently React 19, TypeScript, Vite, TanStack Query, Tailwind CSS 4, Radix UI and Lucide, with Vitest and Testing Library.

## Implementation standards
- Keep domain/API/permission rules separate from presentation components.
- Prefer shared design tokens and composable primitives; avoid one-off hardcoded styles and duplicated screens.
- Preserve URL navigation and browser Back/Forward, focus and unsaved-change protection.
- For right-side detail or quick-create drawers: deterministic open/close state, deep linking where appropriate, focus management and error handling.
- For Kanban: validate transitions with server state; offer keyboard alternative and rollback on failure. Avoid optimistic updates to payments, inventory or irreversible actions without reconciliation.
- For interactive charts: keyboard accessible details, tooltips not as the sole way to read data, stable labels and real filtered drilldowns.
- Query keys, invalidation, stale data and race conditions must be tested.
- Test component behavior and meaningful end-to-end workflows. Do not claim tests passed unless executed.
- Verify mobile and desktop, loading/error/empty states and permission variations.

## Workflow
Inspect source → design contract → small change → tests/typecheck/lint/build as available → document results and remaining risks → PR. No unrelated backend modifications.
