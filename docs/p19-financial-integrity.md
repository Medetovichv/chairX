# P19 — Financial integrity: operational notes

## Cash Flow contract
`GET /api/finance/cash-flow?from=YYYY-MM-DD&to=YYYY-MM-DD` is half-open
`[from,to)` by the **Asia/Bishkek** local calendar. Its source is exclusively
`finance_movements.created_at`. This is the posting timestamp, **not**
`expenses.expense_date`, `payments.paid_at` or `refunds.refunded_at`.

Existing response field names remain stable:
- `payments`: `SALE_PAYMENT` / `PAYMENT` journal credits;
- `paymentCorrections`: absolute debits from `PAYMENT_REVERSAL`;
- `refunds`: absolute `CUSTOMER_REFUND` / `REFUND` debits;
- `exchangePayments` and `exchangeRefunds`: corresponding signed
  `EXCHANGE_SETTLEMENT` journal entries;
- `operatingExpenses`: absolute `EXPENSE` journal debits;
- `totalIn`: payment and exchange credits (gross);
- `totalOut`: **all** customer refunds, payment reversals, exchange refunds
  and operating expense debits; payment reversals are also displayed separately
  in `paymentCorrections` for analysis but are counted **once** in `totalOut`;
- `netCashFlow = totalIn - totalOut`.

`TRANSFER` entries (two opposing postings) and `OPENING_BALANCE` credits
are **excluded** from revenue and expense categories. Managerial expense
reports (`/api/expenses`) continue to use `expenseDate`.

## Expense retry contract
`POST /api/expenses` now requires `idempotencyKey` (UUID). The same key
and semantically equal category, integer amount, method, expense date and
trimmed comment return the original expense without posting again.
Changing any field yields `EXPENSE_IDEMPOTENCY_CONFLICT`, HTTP 409.
Historical expense rows retain `NULL` keys. A standalone new legitimate
expense must use a fresh UUID, even if its amount and date match another.

## Finance operations
`POST /api/finance/opening-balances`: `FINANCE_INITIALIZE`, zero allowed,
initialized once per account, posting only for positive amount.
`POST /api/finance/transfers`: `FINANCE_TRANSFER`, requires independent
account initialization and positive integer amount; both signed
`TRANSFER` movements use one `transferId`. Retrying the same payload
does not post again, including if a different authorized employee retries.

Existing business-day closing persists an immutable expected-versus-actual
snapshot. Explanation is mandatory for discrepancies. It does not silently
change money. Posting operations and closing serialize through existing
account row locks; the business day is Asia/Bishkek.

## Historical reconciliation
The database view `finance_document_posting_issues` detects expected
non-zero document postings that are missing or have a mismatched amount.
The internal read-only `FinanceReconciliationService.listIssues()`
returns document source, document ID, account, expected amount, actual
journal amount, and problem type. It never inserts/backfills movements
or edits closing records. Zero-value payments intentionally have no entry
and are excluded. Existing `OPENING_BALANCE` operations without a money
movement are valid when the initialized balance is zero.

For manual diagnostics, in an authorized administrative SQL session:
```sql
SELECT * FROM finance_document_posting_issues ORDER BY document_source, document_id;
```

## Pending P17 integration: controlled adjustment
A legal adjustment of a reconciled discrepancy needs an approved
`FINANCE_ADJUST` permission (name subject to P17 integration), reason,
original closing reference, responsible actor, immutable journal source,
idempotency key, account lock and posting-time current-day guard.
It must not rewrite historic closing snapshots or silently change
balances. **No adjustment endpoint is provided by P19** because P17
owns permissions and must approve that contract.

## Migration coordination and verification
P19 adds V35 (nullable expense keys and fingerprint with unique partial
index) and V36 (diagnostic view). Before merging with P17, check Flyway
versions on P17 and **renumber new P19 migrations if needed**, without
editing existing already-applied versions. P19 originates from
`fix/p18-business-consistency` and requires P18 regression success.
Run `cd backend && mvn clean verify` against the configured PostgreSQL
test environment; do not merge until it passes.
