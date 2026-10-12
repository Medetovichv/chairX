-- Snapshot only the expenses belonging to the employee at closing time.
-- Historical personal reports do not pull mutable company-wide expense lists.
CREATE TABLE employee_daily_closing_expenses (
  closing_id UUID NOT NULL REFERENCES employee_daily_closings(id),
  expense_id UUID NOT NULL,
  category VARCHAR(50) NOT NULL,
  amount NUMERIC(19,2) NOT NULL CHECK(amount > 0),
  payment_method VARCHAR(20) NOT NULL,
  comment VARCHAR(1000),
  created_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY(closing_id,expense_id)
);
CREATE INDEX idx_employee_daily_closing_expenses_closing ON employee_daily_closing_expenses(closing_id);
CREATE INDEX idx_expenses_owner_day ON expenses(created_by,expense_date);
