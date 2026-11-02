---
name: chairx-dashboard-design
description: Design actionable CRM/ERP dashboards and interactive data visualizations for ChairX.
---
# ChairX Dashboard Design

Design around operational decisions: What needs action now? What changed? Why? What is the next click?

## Visual direction
Calm neutral surfaces, restrained accents, strong typography, readable information density, clearly differentiated status, meaningful charts and subtle transitions. Adopt a consistent system of spacing, border radii, color tokens, chart scales, typography and reusable components. Avoid decorative charts, excessive gradients, motion-only feedback or dashboard clutter.

## Rules
- Every KPI must specify numerator/denominator, business definition, date range, time zone, permission and source API.
- A clickable KPI navigates to a detail page with the matching filter, time range and scope; test that the result count reconciles.
- Charts expose units, axes, intervals, legends, tooltips and drilldowns where supported. Use sensible empty/zero/error states.
- Separate counts of orders, paid amounts, net revenue, margin and cash movements; do not swap them or estimate unavailable numbers.
- Do not show company-wide finance data to staff without permission.
- Support desktop and responsive layouts; prefer meaningful interaction over flashy animation.
- Prefer components already in the frontend; evaluate chart libraries only when requirements justify a new dependency.

## Suggested ChairX home modules (permission gated)
1. Action center: overdue deliveries, unpaid orders and low-stock warnings.
2. Daily operational KPI: orders created, deliveries pending/completed, inventory availability.
3. Orders trend by date with drilldown.
4. Recent or priority sales cards with quick actions.
5. Manager-only finance metrics and trends after verified API availability.

## Acceptance tests
Verify KPI/query consistency, drilldowns, role permissions, empty/zero/loading/error states, keyboard/touch behavior, 320px viewport and large desktop, plus regression on operational screens.
