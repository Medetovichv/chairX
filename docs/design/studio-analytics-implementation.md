# ChairX Studio Analytics — implementation contract

## Approved direction
Studio Analytics is a **complete interactive CRM/ERP interface**, not a color palette or superficial Tailwind-class replacement. The original ChairX brief demands fast everyday work for a 2–4-person chair retailer in Bishkek, plus management reporting: sales, delivery, stock, purchases, customers, returns and finance. All KPIs and filters must use real APIs and lead to the correct record set.

## Layout vocabulary
- **Workspace shell:** differentiated dark navigation, organized permission-filtered sections, precise active/expanded states, consistent Lucide line icons, mobile focus containment and usable narrow layout.
- **Command center:** intentional hero with primary real business signal and visible action, role-aware KPI hierarchy, interactive time series, problems requiring attention, recent records, quick actions. No fabricated profit or revenue from order counts.
- **Operational pages:** action-first page headers, searchable meaningful records, side quick-view, status/ownership clarity, context-preserving URLs and responsive table-or-card views. Rebuild markup/component architecture rather than simply tint old tables.
- **Finance:** dedicated finance layout and visuals derived from finance API; never invent computed profit or mutate FIFO, refunds, day-closing, date logic or report totals to fit the visual.
- **Create/edit flows:** clear required fields, error recovery, server-side validation, unsaved-changes protection, touch-safe actions and role-aware controls.

## Reusable semantics
Use semantic Studio tokens in Tailwind and shared component primitives. Avoid page-by-page arbitrary palettes and structural emoji. Status color must never carry meaning alone. Never expose UUIDs as an input workflow. Real app Russian copy, som (KGS), and Asia/Bishkek business dates remain authoritative.

## Acceptance gates before calling each section redesigned
1. **Visual fidelity:** layout, information hierarchy, chart/table/kanban treatment, iconography and mobile composition are visibly different from the pre-redesign screen and coherent with the Studio reference. A palette-only PR fails this requirement.
2. **Real data:** all statistics come from supported existing APIs; role-filtered or absent fields are not transformed into assumed zero, unauthorized totals are not shown.
3. **Interaction:** at least one real primary action; hyperlinks, KPI drilldowns, filters, side panels, keyboard, Back/Forward and loading/empty/error states work.
4. **Responsive:** screenshots at 375 and 1440px plus overflow checks across 320/375/768/1024/1440; inspect actual screenshots, not only CI assertions.
5. **Regression:** TypeScript, ESLint, Vitest, role tests, backend integration, seeded Chromium F11 and Docker smoke green. Merge only after confirmed CI.
6. **Reviewable:** include before/after screenshots or exact CI artifact links in the PR review notes, list known gaps and record the next section.

## Skills applied
- **Impeccable**: craft-floor contrast, typographic hierarchy, expressive non-template composition, browser details, disabled/loading/empty/error states.
- **UI UX Pro Max**: task efficiency, logical grouping, accessible navigation/labels/focus, platform-appropriate hit targets, status semantics.
- **Vercel React Best Practices**: stable query keys, route splitting, data-dependent rendering, no needless fetches or state coupling, tested performance.

## Rollout order
1. Command center + shell (this PR).
2. Sales and deliveries: operational workspaces, quick-view/kanban, creation forms and workflows.
3. Inventory, catalogue, purchases, receipts and suppliers.
4. Customers, returns, defects.
5. Finance and closing day; administration.
6. Full desktop/mobile review and performance regression.

Each phase must meet its own visual and functional gates; do not mark every page redesigned when only global shell styles change.
