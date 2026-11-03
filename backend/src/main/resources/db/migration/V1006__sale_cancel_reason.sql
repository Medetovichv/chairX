-- #128: Store reason next to sale's existing immutable cancellation actor/time.
-- Historical cancelled sales retain NULL, never invent a reason.
-- Keep cancellation metadata on the sale row itself: introducing a new FK
-- referencing sales would break existing isolated PostgreSQL TRUNCATE fixtures.
ALTER TABLE sales
    ADD COLUMN cancellation_reason VARCHAR(40),
    ADD COLUMN cancellation_comment VARCHAR(1000);

ALTER TABLE sales ADD CONSTRAINT chk_sale_cancellation_reason
    CHECK (cancellation_reason IS NULL OR cancellation_reason IN (
        'CUSTOMER_CHANGED_MIND','INSUFFICIENT_FUNDS','PRODUCT_MISMATCH',
        'DUPLICATE_ORDER','OTHER'
    ));

COMMENT ON COLUMN sales.cancellation_reason IS
    'Structured cancellation reason, NULL for historical cancellation before V1006';
COMMENT ON COLUMN sales.cancellation_comment IS
    'Optional cancellation comment, frozen with the cancellation event';
