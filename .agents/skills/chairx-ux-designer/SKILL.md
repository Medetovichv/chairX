---
name: chairx-ux-designer
description: Product and interaction design for the ChairX CRM/ERP before implementing user-facing pages.
---
# ChairX UX Designer

## Purpose
Do not treat every module as an Excel-like list. Understand the role and workflow first, then choose the best interaction model. Use the CRM inspiration screenshots supplied in conversation as visual direction: compact information-rich cards, navigable metrics, contextual actions, elegant charts, restrained motion, calm surfaces. Do not copy the sample's B2B invoice terminology or invent capabilities.

## Required process
1. Identify the user role (sales clerk, warehouse clerk, courier, manager, admin), task and desired decision.
2. Audit current implementation and actual supported data. List friction and missed opportunities before proposing visuals.
3. Generate at least two feasible interaction options for substantial redesigns and explain their trade-offs. Make a recommendation.
4. Map each visible control to a real behavior: drilldown, side panel, filtering, editing, navigation, or explicit read-only status.
5. Design desktop first for high-frequency workflows; verify tablet/mobile and keyboard.
6. Specify loading, empty, error, permission-denied, validation and unsaved-change states.
7. Define acceptance criteria for flow, responsiveness, accessibility and tests.
8. Implement only after scope is clear; preserve existing operational logic.

## Representation choice
- Kanban: stage-based work where moving between valid states is meaningful; enforce backend transition rules. Never allow drag/drop to bypass payment/delivery requirements.
- Table: high-density reconciliation, inventories and multi-column comparisons; add saved filters and meaningful actions.
- Cards/grid: recognition-based work (customer or product catalogue) with clear metadata.
- Timeline/activity: delivery and customer history.
- Calendar/schedule: date-first delivery planning.
- Dashboard: questions and actions first; no decorative analytics.

## ChairX scenarios
- Sales: fast create, in-stock model selection, readable order IDs, customer/contact, payment, returns, right-hand detail drawer.
- Deliveries: today's work incl. delivered, delayed items, region/city and courier tasks; statuses have clear meaning.
- Inventory: on-hand vs available, reservations, low stock, movement history.
- Customers: purchase history and one-click new sale; show phone when name absent.
- Finance: current vs historical totals, refunds on refund date, role-based visibility; never claim net revenue equals gross sales.
- Daily closing: guided verification before irreversible close.

## Deliverable
A short UX audit, proposed page structure, navigation map, interaction specification and testable acceptance criteria. Avoid a generic 'make it modern' result.
