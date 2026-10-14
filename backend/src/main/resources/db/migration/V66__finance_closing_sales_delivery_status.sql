-- Retain the actual delivery state as part of immutable financial sales snapshots.
ALTER TABLE finance_daily_closing_sales ADD COLUMN delivery_status VARCHAR(30);
