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
