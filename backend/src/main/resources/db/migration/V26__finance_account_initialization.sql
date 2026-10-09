ALTER TABLE finance_accounts
    ADD COLUMN opening_balance_initialized BOOLEAN NOT NULL DEFAULT FALSE;