CREATE TABLE expenses (
                          id UUID PRIMARY KEY,

                          category VARCHAR(30) NOT NULL,
                          amount NUMERIC(19, 2) NOT NULL,
                          payment_method VARCHAR(20) NOT NULL,

                          expense_date DATE NOT NULL,
                          comment VARCHAR(1000),

                          created_by VARCHAR(200) NOT NULL,
                          created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                          CONSTRAINT chk_expense_category
                              CHECK (category IN (
                                                  'ADVERTISING',
                                                  'RENT',
                                                  'SALARY',
                                                  'DELIVERY',
                                                  'UTILITIES',
                                                  'OTHER'
                                  )),

                          CONSTRAINT chk_expense_amount
                              CHECK (amount > 0 AND amount = trunc(amount)),

                          CONSTRAINT chk_expense_payment_method
                              CHECK (payment_method IN ('CASH', 'BANK'))
);

CREATE INDEX idx_expenses_date
    ON expenses (expense_date);