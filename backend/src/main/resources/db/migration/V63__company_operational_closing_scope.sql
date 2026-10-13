-- Promote employee closing to a once-per-company operational confirmation.
-- Old per-person records remain readable; new company-wide records are unique by date.
ALTER TABLE employee_daily_closings
  ADD COLUMN company_wide BOOLEAN NOT NULL DEFAULT FALSE;
CREATE UNIQUE INDEX uq_company_operational_closing_date
  ON employee_daily_closings(business_date) WHERE company_wide=TRUE;
-- Preserve the creator of each employee expense in the immutable operational snapshot.
ALTER TABLE employee_daily_closing_expenses
  ADD COLUMN created_by VARCHAR(200);
UPDATE employee_daily_closing_expenses snapshot
SET created_by=expense.created_by
FROM expenses expense
WHERE expense.id=snapshot.expense_id;
