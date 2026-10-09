CREATE TABLE finance_transfers (
                                   id UUID PRIMARY KEY,

                                   from_account VARCHAR(10) NOT NULL
                                       REFERENCES finance_accounts(code),

                                   to_account VARCHAR(10) NOT NULL
                                       REFERENCES finance_accounts(code),

                                   amount NUMERIC(19, 2) NOT NULL,

                                   created_by VARCHAR(200) NOT NULL,

                                   created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                   CONSTRAINT chk_finance_transfer_accounts
                                       CHECK (from_account <> to_account),

                                   CONSTRAINT chk_finance_transfer_amount
                                       CHECK (amount > 0 AND amount = trunc(amount))
);