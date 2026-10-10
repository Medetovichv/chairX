-- P19-A: historical expenses remain valid with NULL idempotency keys.
-- Only new API operations require an explicit key. Never derive keys from
-- amount, date or comment because equal independent expenses are legitimate.
ALTER TABLE expenses ADD COLUMN idempotency_key UUID;
ALTER TABLE expenses ADD COLUMN request_fingerprint VARCHAR(64);
ALTER TABLE expenses ADD CONSTRAINT chk_expense_idempotency_fingerprint
    CHECK ((idempotency_key IS NULL AND request_fingerprint IS NULL)
        OR (idempotency_key IS NOT NULL AND request_fingerprint ~ '^[a-f0-9]{64}$'));
CREATE UNIQUE INDEX uq_expenses_idempotency_key
    ON expenses (idempotency_key) WHERE idempotency_key IS NOT NULL;
