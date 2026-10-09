ALTER TABLE finance_movements
DROP CONSTRAINT chk_finance_movement_type;

ALTER TABLE finance_movements
    ADD CONSTRAINT chk_finance_movement_type
        CHECK (movement_type IN (
                                 'OPENING_BALANCE',
                                 'SALE_PAYMENT',
                                 'CUSTOMER_REFUND',
                                 'EXPENSE',
                                 'TRANSFER',
                                 'PAYMENT_REVERSAL'
            ));

ALTER TABLE finance_movements
DROP CONSTRAINT chk_finance_movement_source;

ALTER TABLE finance_movements
    ADD CONSTRAINT chk_finance_movement_source
        CHECK (source_type IN (
                               'OPENING_BALANCE',
                               'PAYMENT',
                               'REFUND',
                               'EXPENSE',
                               'TRANSFER',
                               'EXCHANGE_SETTLEMENT'
            ));