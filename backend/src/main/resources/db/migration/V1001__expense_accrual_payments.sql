-- Link existing, already posted expense documents to accruals.
-- Linking never creates or modifies finance_movements.
CREATE TABLE expense_accrual_payments (
  accrual_id UUID NOT NULL REFERENCES expense_accruals(id) ON DELETE RESTRICT,
  expense_id UUID NOT NULL UNIQUE REFERENCES expenses(id) ON DELETE RESTRICT,
  linked_by VARCHAR(255) NOT NULL,
  linked_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  PRIMARY KEY (accrual_id, expense_id)
);
CREATE INDEX ix_expense_accrual_payments_accrual ON expense_accrual_payments(accrual_id);
