-- Administrative corrections are financial movements, never silent balance rewrites.
CREATE TABLE finance_admin_adjustments (
    id UUID PRIMARY KEY,
    account_code VARCHAR(20) NOT NULL REFERENCES finance_accounts(code),
    previous_balance NUMERIC(19,2) NOT NULL,
    new_balance NUMERIC(19,2) NOT NULL,
    delta NUMERIC(19,2) NOT NULL,
    reason VARCHAR(500) NOT NULL CHECK (length(btrim(reason)) >= 5),
    created_by VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT ck_finance_adjustment_nonnegative CHECK (new_balance >= 0),
    CONSTRAINT ck_finance_adjustment_delta CHECK (previous_balance + delta = new_balance)
);

-- Extend the already-established F10 movement enum checks without weakening them.
ALTER TABLE finance_movements DROP CONSTRAINT chk_finance_movement_type;
ALTER TABLE finance_movements ADD CONSTRAINT chk_finance_movement_type
    CHECK (movement_type IN (
        'SALE_PAYMENT','CUSTOMER_REFUND','EXPENSE','TRANSFER',
        'PAYMENT_REVERSAL','PURCHASE_PAYMENT','OPENING_BALANCE',
        'ADMIN_BALANCE_ADJUSTMENT'
    ));
ALTER TABLE finance_movements DROP CONSTRAINT chk_finance_movement_source;
ALTER TABLE finance_movements ADD CONSTRAINT chk_finance_movement_source
    CHECK (source_type IN (
        'PAYMENT','REFUND','EXPENSE','TRANSFER','EXCHANGE_SETTLEMENT',
        'PAYMENT_REVERSAL','OPENING_BALANCE','PURCHASE_PAYMENT',
        'ADMIN_BALANCE_ADJUSTMENT'
    ));
