-- Keep the immutable financial posting date while assigning late staff expenses
-- to the next *operational* report once today's company report is confirmed.
ALTER TABLE expenses ADD COLUMN operational_report_date DATE;
UPDATE expenses SET operational_report_date = expense_date;
CREATE INDEX idx_expenses_operational_report_date ON expenses(operational_report_date, created_at);
