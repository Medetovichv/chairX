-- P20-A. Purchases and their physical receipts remain independent from payments.
CREATE TABLE purchase_payments (
    id UUID PRIMARY KEY,
    purchase_id UUID NOT NULL REFERENCES purchases(id),
    payment_kind VARCHAR(10) NOT NULL CHECK (payment_kind IN ('SUPPLIER','CARGO')),
    account_code VARCHAR(10) NOT NULL REFERENCES finance_accounts(code),
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0 AND amount = trunc(amount)),
    idempotency_key UUID NOT NULL UNIQUE,
    request_fingerprint VARCHAR(64) NOT NULL CHECK (request_fingerprint ~ '^[a-f0-9]{64}$'),
    reference VARCHAR(200),
    comment VARCHAR(1000),
    created_by VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX idx_purchase_payments_purchase ON purchase_payments(purchase_id,created_at,id);

ALTER TABLE finance_movements DROP CONSTRAINT chk_finance_movement_type;
ALTER TABLE finance_movements ADD CONSTRAINT chk_finance_movement_type
    CHECK (movement_type IN ('SALE_PAYMENT','CUSTOMER_REFUND','EXPENSE',
                            'TRANSFER','PAYMENT_REVERSAL','PURCHASE_PAYMENT',
                            'OPENING_BALANCE'));
ALTER TABLE finance_movements DROP CONSTRAINT chk_finance_movement_source;
ALTER TABLE finance_movements ADD CONSTRAINT chk_finance_movement_source
    CHECK (source_type IN ('PAYMENT','REFUND','EXPENSE','TRANSFER',
                          'EXCHANGE_SETTLEMENT','PAYMENT_REVERSAL',
                          'OPENING_BALANCE','PURCHASE_PAYMENT'));
