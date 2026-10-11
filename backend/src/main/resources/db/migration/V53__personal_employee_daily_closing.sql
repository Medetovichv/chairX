-- Personal daily cash collection is deliberately separate from the company's
-- finance_daily_closings and must never mutate finance_accounts or ledger rows.
-- A snapshot is tied to immutable employee UUID (not a client-provided username).
CREATE TABLE employee_daily_closings (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES app_users(id),
    business_date DATE NOT NULL,
    expected_cash NUMERIC(19,2) NOT NULL,
    expected_bank NUMERIC(19,2) NOT NULL,
    actual_cash NUMERIC(19,2) NOT NULL,
    actual_bank NUMERIC(19,2) NOT NULL,
    cash_note VARCHAR(2000),
    bank_note VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_employee_daily_close UNIQUE (employee_id,business_date),
    CONSTRAINT chk_employee_daily_amounts CHECK (
        expected_cash >= 0 AND expected_bank >= 0
        AND actual_cash >= 0 AND actual_bank >= 0
        AND expected_cash=trunc(expected_cash) AND expected_bank=trunc(expected_bank)
        AND actual_cash=trunc(actual_cash) AND actual_bank=trunc(actual_bank)
    ),
    CONSTRAINT chk_employee_daily_notes CHECK (
        (expected_cash=actual_cash OR (cash_note IS NOT NULL AND length(btrim(cash_note))>0))
        AND (expected_bank=actual_bank OR (bank_note IS NOT NULL AND length(btrim(bank_note))>0))
    )
);
CREATE INDEX idx_employee_daily_closings_date ON employee_daily_closings(business_date DESC);
CREATE TABLE employee_daily_closing_sales (
    closing_id UUID NOT NULL REFERENCES employee_daily_closings(id),
    sale_id UUID NOT NULL REFERENCES sales(id),
    sale_number VARCHAR(30) NOT NULL,
    quantity BIGINT NOT NULL,
    total NUMERIC(19,2) NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (closing_id,sale_id)
);
