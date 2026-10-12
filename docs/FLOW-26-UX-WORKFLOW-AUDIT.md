# FLOW-26 — UX/workflow audit and release boundaries

## Verified source findings

1. **Purchases:** `PurchaseOverviewService.list(..., number)` filters `p.id::text ILIKE :number`; it is NOT a human-readable purchase number. Therefore the UI UUID search was removed rather than misleadingly relabeled. Existing status, supplier and date backend filters are retained; future human-readable purchase numbers require a backend field/migration.
2. **Physical return → exchange:** `ExchangeService.create` requires a PAID original sale, unused `returnId` (not already refunded or exchanged), available settled amount, and inventory for the new sale. The UI formerly asked for a UUID even when entry originated in a return card. Replaced UUID form with navigation and an explicit selected-return preview, plus preflight guidance. Full automated reproduction of the user's particular failure still needs captured API status/error.
3. **Expenses:** backend requires initialization of the chosen financial account before expense posting. UI now shows an actionable setup state; only users with FINANCE_INITIALIZE see the configuration link. The backend remains authoritative.
4. **Cash flow:** the existing API contains **period aggregates only**, not a paginated journal. Redesigned as financial movement summary and inflow/outflow breakdown without inventing transactions or equating cash flow with profit.
5. **Sales:** secondary cancellation and refund controls are now collapsed. They remain permission-gated and behind existing guarded confirmation.
6. **Account opening:** the present API permits one-time initialization only. It does not permit altering initialized balances. Admin-initiated change requires a new **audited adjustment transaction**, with a reason, account-row locking, closed-day protections and tests; never overwrite balances directly. Admin-only authorization must be enforced server-side, not just by hiding frontend links.
7. **Delivery in sale creation:** current sale draft API and delivery API are two different operations, and delivery creation requires a confirmed delivery-type sale. A safe single-screen flow needs a backend transactional orchestration or at least staged, idempotent confirmation; do not send two non-idempotent requests from initial draft form without recovery guarantees.

## Acceptance still required
- [ ] Frontend lint / TypeScript / Vitest / build pass.
- [ ] Backend + F09 + F10 + F11 pass.
- [ ] Return/exchange failure reproduction and state-specific tests.
- [ ] Admin-only balance adjustment design/implementation, migration and tests.
- [ ] Single-screen delivery fields at sale creation and transactional orchestration.
- [ ] Manual mobile acceptance.

**Do not mark this PR complete or merge it based solely on visual changes.**
