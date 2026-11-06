-- Every committed receipt identifies one exact client operation and its outcome.
-- The placeholder and business mutation must commit in the same transaction.
CREATE TABLE sale_operation_receipts (
    operation_id UUID PRIMARY KEY,
    -- This is an immutable operational receipt. Avoid an FK so existing
    -- isolated PostgreSQL TRUNCATE fixtures can reset sales independently.
    sale_id UUID NOT NULL,
    actor VARCHAR(200) NOT NULL,
    action VARCHAR(32) NOT NULL,
    request_fingerprint TEXT NOT NULL,
    response_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_sale_operation_receipt_action
        CHECK (action IN ('confirm', 'fulfill', 'cancel')),
    CONSTRAINT ck_sale_operation_receipt_actor
        CHECK (length(btrim(actor)) > 0)
);
CREATE INDEX idx_sale_operation_receipts_sale
    ON sale_operation_receipts (sale_id, created_at DESC);
