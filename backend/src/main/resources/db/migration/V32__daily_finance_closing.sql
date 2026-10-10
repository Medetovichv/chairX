CREATE TABLE finance_daily_closings (
 id UUID PRIMARY KEY,
 business_date DATE NOT NULL UNIQUE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 created_by VARCHAR(200) NOT NULL,
 CONSTRAINT chk_closing_actor CHECK (length(trim(created_by)) > 0)
);
CREATE TABLE finance_daily_closing_accounts (
 closing_id UUID NOT NULL REFERENCES finance_daily_closings(id),
 account_code VARCHAR(10) NOT NULL REFERENCES finance_accounts(code),
 expected_balance NUMERIC(19,2) NOT NULL,
 actual_balance NUMERIC(19,2) NOT NULL,
 difference NUMERIC(19,2) GENERATED ALWAYS AS (actual_balance - expected_balance) STORED,
 note VARCHAR(2000),
 PRIMARY KEY (closing_id, account_code),
 CONSTRAINT chk_closing_balances CHECK (expected_balance >= 0 AND actual_balance >= 0 AND expected_balance = trunc(expected_balance) AND actual_balance = trunc(actual_balance)),
 CONSTRAINT chk_closing_note CHECK (actual_balance = expected_balance OR (note IS NOT NULL AND length(trim(note)) > 0))
);
CREATE INDEX idx_finance_closing_date ON finance_daily_closings(business_date DESC);
