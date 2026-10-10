-- Legacy payments deliberately retain NULL channel (mapped to the
-- generic historical CASH/BANK_TRANSFER label by the application).
ALTER TABLE payments ADD COLUMN payment_channel VARCHAR(30);
ALTER TABLE payments ADD CONSTRAINT chk_sale_payment_channel
    CHECK (payment_channel IS NULL
        OR (method='CASH' AND payment_channel='CASH')
        OR (method='TRANSFER' AND payment_channel IN
            ('BANK_TRANSFER','MBANK','BANK_INSTALLMENT')));
