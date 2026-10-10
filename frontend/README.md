# ChairX frontend — F01 Foundation

Frontend for the small ChairX ERP/CRM. **F01 is navigation and infrastructure only**: business screens are placeholders with no invented operational data. Interface language: Russian; currency: KGS; business timezone: Asia/Bishkek.

## Requirements and setup

- Node.js **24.x** (CI uses Node 24).
- npm (bundled with Node 24).
- Backend (optional for F01 UI) running on http://localhost:8080 with PostgreSQL 17 according to the root README.

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

The backend still uses HTTP Basic and Spring Security CSRF. Existing endpoint GET /api/csrf is documented in docs/api.md. F01 intentionally has no login page or fake /api/auth/me. The client supports injected getAuthHeaders and getCsrfHeaders providers for F02, but F01 supplies none. Authenticated business API calls are NOT intended to work from the UI in F01; all business screens remain placeholders. Credentials are never stored in localStorage/sessionStorage.

API requests must use existing paths beginning with /api. The default client uses same-origin credentials with the proxy; cross-origin deployments require separately reviewed CORS/credential settings. Errors from ApiError(code, message, details) are preserved as typed ApiClientError(status, code, details), while displayed Russian messages avoid unsafe raw server output.

## Project structure

- src/app/providers — TanStack Query and router providers
- src/app/router — app routes from the shared navigation model
- src/layouts — desktop sidebar, header, mobile drawer, layout
- src/pages — welcome, upcoming business screens, 404
- src/shared/api — typed fetch client, error handling and query defaults
- src/shared/components — loading, empty and error states
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

## Scope boundary: next F02

Implement authentication deliberately, with a reviewed browser-safe strategy for Basic credentials, CSRF token retrieval via the existing GET /api/csrf endpoint and proper cookie/header behavior. Authoritative access control remains on backend. Never place credentials in persistent browser storage or weaken backend security to make a UI call work. F02 should provide authorized session behavior, 401/403 handling at screen level and auth tests before enabling real features.

