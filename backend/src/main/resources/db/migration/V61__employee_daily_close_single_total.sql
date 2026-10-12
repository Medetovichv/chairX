-- Personal confirmation no longer fabricates a CASH/BANK split.
-- Legacy reports continue to retain their two independently attested amounts.
ALTER TABLE employee_daily_closings
    ADD COLUMN actual_total NUMERIC(19,2),
    ADD COLUMN total_note VARCHAR(2000);
ALTER TABLE employee_daily_closings
    ALTER COLUMN actual_cash DROP NOT NULL,
    ALTER COLUMN actual_bank DROP NOT NULL;
ALTER TABLE employee_daily_closings DROP CONSTRAINT chk_employee_daily_amounts;
ALTER TABLE employee_daily_closings DROP CONSTRAINT chk_employee_daily_notes;
ALTER TABLE employee_daily_closings
    ADD CONSTRAINT chk_employee_daily_amounts CHECK (
        expected_cash >= 0 AND expected_bank >= 0
        AND expected_cash = trunc(expected_cash) AND expected_bank = trunc(expected_bank)
        AND (
          (actual_total IS NULL AND actual_cash IS NOT NULL AND actual_bank IS NOT NULL
            AND actual_cash >= 0 AND actual_bank >= 0
            AND actual_cash=trunc(actual_cash) AND actual_bank=trunc(actual_bank))
          OR
          (actual_total IS NOT NULL AND actual_cash IS NULL AND actual_bank IS NULL
            AND actual_total>=0 AND actual_total=trunc(actual_total))
        )
    );
ALTER TABLE employee_daily_closings
    ADD CONSTRAINT chk_employee_daily_notes CHECK (
       (actual_total IS NOT NULL
          AND (actual_total=expected_cash+expected_bank OR
               (total_note IS NOT NULL AND length(btrim(total_note))>0)))
       OR
       (actual_total IS NULL AND
        (expected_cash=actual_cash OR (cash_note IS NOT NULL AND length(btrim(cash_note))>0))
        AND (expected_bank=actual_bank OR (bank_note IS NOT NULL AND length(btrim(bank_note))>0)))
    );
