ALTER TABLE sales
    ADD COLUMN idempotency_key UUID NOT NULL,
    ADD COLUMN request_fingerprint VARCHAR(64) NOT NULL;

ALTER TABLE sales
    ADD CONSTRAINT uq_sales_idempotency_key
        UNIQUE (idempotency_key);

ALTER TABLE sales
    ADD CONSTRAINT ck_sales_request_fingerprint_not_blank
        CHECK (length(btrim(request_fingerprint)) > 0);