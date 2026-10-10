-- P19-D. Read-only diagnosis: never backfill legacy cash movements based
-- only on documents, and never change a closed business day's balances.
-- Zero-value payments have no financial posting by design.
CREATE VIEW finance_document_posting_issues AS
WITH expected AS (
    SELECT 'PAYMENT'::varchar AS document_source,
           p.id AS document_id,
           (CASE WHEN p.method = 'CASH' THEN 'CASH' ELSE 'BANK' END)::varchar AS account_code,
           'SALE_PAYMENT'::varchar AS movement_type,
           'PAYMENT'::varchar AS movement_source,
           p.amount AS expected_amount
    FROM payments p WHERE p.amount > 0
    UNION ALL
    SELECT 'PAYMENT_REVERSAL', p.id,
           CASE WHEN p.method = 'CASH' THEN 'CASH' ELSE 'BANK' END,
           'PAYMENT_REVERSAL', 'PAYMENT_REVERSAL', -p.amount
    FROM payments p WHERE p.status = 'CANCELLED' AND p.amount > 0
    UNION ALL
    SELECT 'REFUND', r.id,
           CASE WHEN r.method = 'CASH' THEN 'CASH' ELSE 'BANK' END,
           'CUSTOMER_REFUND', 'REFUND', -r.amount
    FROM refunds r WHERE r.amount > 0
    UNION ALL
    SELECT 'EXPENSE', e.id, e.payment_method, 'EXPENSE', 'EXPENSE', -e.amount
    FROM expenses e WHERE e.amount > 0
    UNION ALL
    SELECT 'EXCHANGE_SETTLEMENT', x.id,
           CASE WHEN x.method = 'CASH' THEN 'CASH' ELSE 'BANK' END,
           CASE WHEN x.direction = 'IN' THEN 'SALE_PAYMENT' ELSE 'CUSTOMER_REFUND' END,
           'EXCHANGE_SETTLEMENT',
           CASE WHEN x.direction = 'IN' THEN x.amount ELSE -x.amount END
    FROM exchange_settlements x WHERE x.amount > 0
)
SELECT e.document_source, e.document_id, e.account_code,
       e.movement_type, e.expected_amount,
       COALESCE(j.posted_amount, 0)::numeric(19,2) AS posted_amount,
       CASE WHEN j.entry_count IS NULL THEN 'MISSING'
            ELSE 'AMOUNT_MISMATCH' END::varchar AS problem
FROM expected e
LEFT JOIN LATERAL (
    SELECT SUM(m.amount) AS posted_amount, COUNT(*) AS entry_count
    FROM finance_movements m
    WHERE m.source_type = e.movement_source
      AND m.source_id = e.document_id
      AND m.account_code = e.account_code
      AND m.movement_type = e.movement_type
    HAVING COUNT(*) > 0
) j ON TRUE
WHERE j.entry_count IS NULL OR j.posted_amount <> e.expected_amount;
