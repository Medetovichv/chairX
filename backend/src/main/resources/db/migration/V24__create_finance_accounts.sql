CREATE TABLE finance_accounts (
                                  code VARCHAR(10) PRIMARY KEY,
                                  balance NUMERIC(19, 2) NOT NULL DEFAULT 0,

                                  CONSTRAINT chk_finance_account_code
                                      CHECK (code IN ('CASH', 'BANK')),

                                  CONSTRAINT chk_finance_account_balance
                                      CHECK (balance >= 0 AND balance = trunc(balance))
);

INSERT INTO finance_accounts (code, balance)
VALUES ('CASH', 0), ('BANK', 0);

CREATE TABLE finance_movements (
                                   id UUID PRIMARY KEY,

                                   account_code VARCHAR(10) NOT NULL
                                       REFERENCES finance_accounts(code),

                                   amount NUMERIC(19, 2) NOT NULL,

                                   movement_type VARCHAR(30) NOT NULL,

                                   source_type VARCHAR(30) NOT NULL,
                                   source_id UUID NOT NULL,

                                   created_by VARCHAR(200) NOT NULL,
                                   created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                   CONSTRAINT chk_finance_movement_amount
                                       CHECK (amount <> 0 AND amount = trunc(amount)),

                                   CONSTRAINT chk_finance_movement_type
                                       CHECK (movement_type IN (
                                                                'SALE_PAYMENT',
                                                                'CUSTOMER_REFUND',
                                                                'EXPENSE',
                                                                'TRANSFER',
                                                                'PAYMENT_REVERSAL'
                                           )),

                                   CONSTRAINT chk_finance_movement_source
                                       CHECK (source_type IN (
                                                              'PAYMENT',
                                                              'REFUND',
                                                              'EXPENSE',
                                                              'TRANSFER',
                                                              'EXCHANGE_SETTLEMENT'
                                           )),

                                   CONSTRAINT uq_finance_movement_source_account
                                       UNIQUE (source_type, source_id, account_code)
);

CREATE INDEX idx_finance_movements_account_date
    ON finance_movements(account_code, created_at);