-- F14: accruals are accounting recognition, NOT cash/bank movements.
CREATE TABLE expense_accruals (
  id UUID PRIMARY KEY,
  idempotency_key UUID NOT NULL UNIQUE,
  category VARCHAR(40) NOT NULL CHECK (category IN ('ADVERTISING','RENT','SALARY','DELIVERY','UTILITIES','OTHER')),
  description VARCHAR(1000) NOT NULL,
  amount NUMERIC(19,0) NOT NULL CHECK (amount > 0),
  period_start DATE NOT NULL,
  period_end DATE NOT NULL,
  created_by VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  CHECK (period_end > period_start),
  CHECK (period_end <= period_start + INTERVAL '366 days')
);
CREATE INDEX ix_expense_accruals_period ON expense_accruals(period_start,period_end);
