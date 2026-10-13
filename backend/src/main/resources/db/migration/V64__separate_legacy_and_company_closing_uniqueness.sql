-- Legacy personal reports and the one company operational confirmation
-- may legitimately share an employee and business date during the upgrade.
-- Keep each scope unique without dropping or rewriting historical records.
ALTER TABLE employee_daily_closings
    DROP CONSTRAINT uq_employee_daily_close;
CREATE UNIQUE INDEX uq_employee_legacy_daily_close
    ON employee_daily_closings(employee_id,business_date)
    WHERE company_wide=FALSE;
-- V63 already enforces a single company-wide confirmation per business_date.
