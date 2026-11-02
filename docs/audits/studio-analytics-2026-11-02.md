# ChairX Studio Analytics — source-level audit (2026-11-02)

## Scope and evidence
Review of the current `main` after merges #130–141. Inspected representative pages (Sales, Finance Overview/Analytics, Daily Closing company and employee views, Purchase Payments, Defects), shared AppLayout, Button, Input, CSS and routing. Read the Impeccable 4.8, UI UX Pro Max, and Vercel React Best Practices skill instructions previously integrated in PR #129's skill branch. These skills are **not available in main** as executable workflows. No local launcher, browser screenshot, Playwright, axe or Lighthouse run was performed in this GitHub-connector-only audit. CI green on individual PR heads is not proof of browser rendering or integration on merged `main`.

## Severity-ranked findings

### P1 — Design-system fragmentation across pages
**Evidence:** `frontend/src/shared/ui/button.tsx` still uses `bg-indigo-600`, `hover:bg-indigo-700`, and indigo focus. `frontend/src/shared/ui/input.tsx` uses indigo focus and old slate surfaces. The new Sales page has ~22 inline hex color literals and its own pill/button overrides; AppLayout retains `#f5f9fe`. `frontend/src/index.css` retains blue hover/selection/focus. 
**Impact:** inconsistent hover, pressed, focus and keyboard states between screens; future edits multiply one-off styles.
**Action:** introduce semantic CSS/Tailwind tokens for background, surface, text, border, brand, focus, and status; refactor shared Button/Input first; migrate pages without changing API or workflow. Add component and responsive snapshot tests.

### P1 — Purchase Payments requires manual UUID
**Evidence:** `frontend/src/features/purchases/pages/PurchasePaymentsPage.tsx` lines 54–64 label and placeholder ask for UUID, although the product brief says staff should not see technical identifiers; there is only a link back to Purchases.
**Impact:** high-friction operational workflow; copying private IDs causes errors.
**Action:** selection of purchase via readable order/date/supplier and route `/purchases/payments?purchaseId=...` for deep links; keep UUID internal; test permission filtering, links and invalid selection.

### P1 — No browser/device-level acceptance evidence for cross-page redesign
**Evidence:** all #131–141 PRs were merged after individual CI, but no validated 320/375/768/1024/1440px browser screenshots or axe keyboard walkthroughs are available in this review.
**Impact:** cannot establish visual parity, focus behavior, overflow, chart legibility or right-side drawer usability.
**Action:** reproducible Playwright screenshot suite (with seeded accounts for cashier, warehouse, manager and admin); compare reference Studio dashboard across pages. Include dark/light if supported; capture empty/error/loading and disabled states.

### P2 — Emoji still used as iconography in closing page
**Evidence:** `frontend/src/pages/DailyClosingPage.tsx` contains shopping-cart emoji in KPI heading; `lucide-react` is already used for other pages.
**Impact:** inconsistent rendering and alignment on Android/iOS/Windows.
**Action:** replace semantic emoji icons with Lucide SVG; add `aria-hidden` and retain readable label.

### P2 — Employee daily closing still has old visual treatment
**Evidence:** `frontend/src/features/daily-closing/EmployeeDailyClosingPage.tsx` includes old slate/blue cards and form fields, while company closing was restyled in PR #139.
**Impact:** inconsistent design depending on staff role.
**Action:** migrate employee flow, cross-check company/manager/employee visibility, no finance-rule changes; keyboard and mobile acceptance.

### P2 — Nested finance/purchase data surfaces remain inconsistent
**Evidence:** `PurchasePaymentsPage.tsx` lines 73, 86 still have old slate boxes; `FinanceAnalyticsPage.tsx` includes multiple locally defined visual styles.
**Action:** reusable DataPanel, ResponsiveTable, FilterBar, MetricCard; render visual tokens consistently.

### P2 — Design skills not runnable from current main
**Evidence:** `.agents/skills/impeccable/SKILL.md`, `.agents/skills/ui-ux-pro-max/SKILL.md`, `.agents/skills/vercel-react-best-practices/SKILL.md` unavailable in `main`; skill source has been read from earlier design-skills branch, but runtime setup and CLI context step were not executed.
**Action:** bring approved skill instructions/scripts in a distinct reviewed PR or install supported skills; execute their checklists in CI without network binaries of unknown provenance. Add acceptance artifacts so skill recommendations yield measurable changes.

## Positive confirmed evidence
- Shared router has permission gates and separates detailed routes.
- Button and Input have disabled states, focus styles and touch-height defaults (though wrong palette).
- Sales and Deliveries have URL-backed filters and QuickView drawers; existing list tests cover routes.
- Receiving restricts purchase price visibility for receiving staff.
- Finance reporting still keeps FIFO and missing-data handling instead of inventing profits.
- Individual #131–141 CI workflows reported successful before merge.

## Acceptance plan
1. **Foundation**: tokens, shared controls, input, filter, tabs, focus, error/loading patterns; zero business-logic changes.
2. **Critical UX**: no visible UUID in payments; search/selection, accessible labels, touch targets, low-contrast audit.
3. **Role coverage**: employee/manager daily closing, purchase receiving and finance flows; permissions tests.
4. **Visual QA**: browser desktop/mobile screenshots and comparative review (320, 375, 768, 1024, 1440px), WCAG contrast, keyboard-only workflow, reduced motion, empty and error states.
5. **Regression**: TypeScript, lint, Vitest, backend integrations, Docker smoke and merged-main release gate. Record reports with real pass/fail counts.

## Skill scorecard (evidence-based; NOT a scored automated run)
- Impeccable: visual direction recognized; incomplete craft-floor compliance due to mismatched shared controls, emoji, no browser verification.
- UI UX Pro Max: basic responsive layouts and accessible labels present; identifier workflow, touch/contrast and keyboard/browser validation remain open.
- Vercel React Best Practices: no new dependencies and mostly existing React data flows preserved; performance not measured, no bundle report, no network waterfall trace.
- Testing: strong individual PR CI; missing image-based cross-route acceptance and post-merge end-to-end verification.

**Conclusion:** This release is a good visual foundation, **not yet a fully audited or consistently implemented design system**. Resolve P1 issues before claiming completed UX redesign.
