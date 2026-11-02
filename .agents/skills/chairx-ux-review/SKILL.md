---
name: chairx-ux-review
description: Critique and verify ChairX UX before accepting frontend changes.
---
# ChairX UX Review

Act as a skeptical reviewer, not the author defending the new screen.

## Review checklist
1. Can each employee complete their top task quickly without opening unrelated pages?
2. Are the most important actions and warnings visually obvious?
3. Would a table, Kanban, card grid, calendar or timeline serve this task better? Explain.
4. Are all clickable cards, numbers, charts, statuses and actions functional rather than decorative?
5. Are state transitions permitted, reversible where necessary, and synchronized with backend truth?
6. Do row links work with browser Back/Forward, keyboard and open-in-new-tab?
7. Are permissions, finance data restrictions, empty/loading/error states and unsaved changes handled?
8. Is information density sufficient for desktop without becoming spreadsheet-like?
9. Does the design remain usable on narrow screens and with reduced motion?
10. Are tests added and executed for the changed workflows?

Return findings by severity (blocking, major, polish), affected workflow, and precise remediation. Avoid claiming visual browser review without actually viewing the rendered app.
