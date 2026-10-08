ALTER TABLE exchange_settlements
    ADD COLUMN request_fingerprint VARCHAR(64);

UPDATE exchange_settlements
SET request_fingerprint = repeat('0', 64)
WHERE request_fingerprint IS NULL;

ALTER TABLE exchange_settlements
    ALTER COLUMN request_fingerprint SET NOT NULL;

ALTER TABLE exchange_settlements
    ADD CONSTRAINT chk_exchange_settlement_fingerprint
        CHECK (request_fingerprint ~ '^[0-9a-f]{64}$');