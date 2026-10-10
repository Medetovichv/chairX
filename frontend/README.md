# ChairX frontend — F02.1 Navigation & UX

Frontend for the small ChairX ERP/CRM. **F02.1** includes the F01 foundation, F02 HTTP Basic/CSRF login, and permission-aware navigation with operational page shells. Business screens intentionally do not fetch or mutate operational data. Interface language: Russian; currency: KGS; business timezone: Asia/Bishkek.

## Requirements and setup

- Node.js **24.x** (CI uses Node 24).
- npm (bundled with Node 24).
- Backend (required for F02 sign-in) running on http://localhost:8080 with PostgreSQL 17 according to the root README.

From the repository root:

~~~bash
cd frontend
npm ci
npm run dev
~~~

Open http://localhost:5173. For a standalone production static build: npm run build, then npm run preview. The server serving production frontend **must** route /api to the backend and fall back to index.html for application routes.

Use npm ci with the committed package-lock.json. Do not commit .env, node_modules or dist.

## Environment and API

Copy .env.example to .env.local if needed. VITE_BACKEND_TARGET (default http://localhost:8080) controls the Vite development proxy /api; set VITE_API_BASE_URL only to target a different origin directly. Any VITE_ variable is **public in the browser**, never put secrets or passwords there.

The backend uses HTTP Basic and CSRF. F02 signs users in through
`GET /api/auth/me` and `GET /api/csrf`. Session secrets are held only in
memory, never in browser persistent storage. Navigation and route guards use
the effective permission list returned by the backend. Backend authorization
is always authoritative.

F02.1 replaces generic placeholders with page shells but does not fetch real
sales, finances, customers or inventory, nor implement business mutations.
Nested screen paths and the permission matrix are documented in
[docs/f02-1-navigation.md](../docs/f02-1-navigation.md).

API requests must use existing paths beginning with /api. The default client uses same-origin credentials with the proxy; cross-origin deployments require separately reviewed CORS/credential settings. Errors from ApiError(code, message, details) are preserved as typed ApiClientError(status, code, details), while displayed Russian messages avoid unsafe raw server output.

## Project structure

- src/app/providers — TanStack Query and router providers
- src/app/router — app routes from the shared navigation model
- src/layouts — desktop sidebar, header, mobile drawer, layout
- src/pages — welcome, operational page shells, daily closing, 403/404
- src/shared/api — typed fetch client, error handling and query defaults
- src/shared/components — page headers, tabs, toolbars, status badges, loading, empty and error states
- src/shared/ui — styled Button/Input/ConfirmDialog; shadcn/ui-inspired copied-in components based on Radix and Tailwind
- src/shared/lib — navigation metadata, styling and business formatting
- src/test — Vitest, React Testing Library tests

No Redux, global auth state or over-engineered domain abstractions. Each future business feature should live under src/features/<feature> and reuse shared contracts.

## Quality gates

~~~bash
npm ci
npm run lint
npm run typecheck
npm run test
npm run build
~~~

GitHub Actions runs the same gates for frontend changes to pull requests. Run tests without suppressing errors. Existing backend workflow stays separate.

## Scope boundary: F03 and later

F02.1 is deliberately a **navigation and operational UI structure** package.
Do not mistake visual filters, disabled buttons or empty tables for connected
CRUD functionality. Integrate real API data, money operations and role editing
in later feature packages, with backend permissions on every request.
