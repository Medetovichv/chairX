-- Split actual registration timestamp from the date to which cash movements
-- belong. Never change immutable created_at or overwrite existing movements.
ALTER TABLE finance_movements ADD COLUMN business_date DATE;
ALTER TABLE finance_movements ADD COLUMN business_date_source VARCHAR(20);

-- Existing movements were historically dated only by created_at. Explicitly
-- mark these dates as inferred rather than silently claiming document accuracy.
UPDATE finance_movements
SET business_date = (created_at AT TIME ZONE 'Asia/Bishkek')::date,
    business_date_source = 'LEGACY_INFERRED';

ALTER TABLE finance_movements ALTER COLUMN business_date SET NOT NULL;
ALTER TABLE finance_movements ALTER COLUMN business_date_source SET NOT NULL;
ALTER TABLE finance_movements ALTER COLUMN business_date_source SET DEFAULT 'POSTING_DATE';
ALTER TABLE finance_movements ALTER COLUMN business_date
    SET DEFAULT ((clock_timestamp() AT TIME ZONE 'Asia/Bishkek')::date);

ALTER TABLE finance_movements ADD CONSTRAINT chk_finance_movement_business_date_source
CHECK (business_date_source IN ('LEGACY_INFERRED', 'POSTING_DATE', 'HISTORICAL_CORRECTION'));

CREATE INDEX idx_finance_movements_account_business_date
    ON finance_movements (account_code, business_date);
