# ChairX frontend — F03 Catalog

React 19 + TypeScript + Vite + Tailwind CSS + React Router + TanStack Query.
Existing F01 foundation, F02 memory-only HTTP Basic/CSRF login and F02.1
permission-aware navigation are retained.

**F03 connects /catalog and /catalog/:productId to the real backend**
using the existing ApiClient and Product/Variant REST endpoints. Other
business areas remain F02.1 read-only page shells until their own packages.

## Run locally
Requires Node.js 24, npm and the ChairX Java 25/PostgreSQL 17 backend.
From the repository root:

\`\`\`bash
cd frontend
npm ci
npm run dev
\`\`\`

Open http://localhost:5173. For Docker from repository root:

\`\`\`bash
docker compose up -d --build --wait
\`\`\`

Do not use \`docker compose down -v\` with a database you wish to preserve.

## F03 catalog
- /catalog: real server-paginated product list, create model.
- /catalog/:productId: product detail; paged variants; create/edit/activate/
  deactivate product and variant, confirmation before deactivation.
- Requires \`CATALOG_READ\` to read and \`CATALOG_MANAGE\` to mutate.
- No fake records, no inventory, no client-side pseudo-global search.
  Backend list API currently has only page/size.
- All prices in KGS; exact decimal string sent to Java BigDecimal.
- Backend SecurityConfig remains authoritative; F02 authorization/CSRF are
  preserved in memory, never localStorage/sessionStorage.

See [F03 API, route/permissions, limitations](../docs/f03-product-catalog.md)
and [F02.1 navigation](../docs/f02-1-navigation.md).

## Quality gates
\`\`\`bash
npm ci
npm run lint
npm run typecheck
npm run test
npm run build
\`\`\`

GitHub Actions runs the same frontend gates and a full Docker stack smoke
check on the PR. Runtime network and backend errors use existing safe API
error messages.
