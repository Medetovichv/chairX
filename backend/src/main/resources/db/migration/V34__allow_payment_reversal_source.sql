-- A reversal must have a separate source identity from its original payment.
ALTER TABLE finance_movements DROP CONSTRAINT chk_finance_movement_source;
ALTER TABLE finance_movements ADD CONSTRAINT chk_finance_movement_source
    CHECK (source_type IN ('OPENING_BALANCE', 'PAYMENT', 'PAYMENT_REVERSAL',
                          'REFUND', 'EXPENSE', 'TRANSFER', 'EXCHANGE_SETTLEMENT'));
ALTER TABLE finance_movements ALTER COLUMN created_at SET DEFAULT clock_timestamp();
