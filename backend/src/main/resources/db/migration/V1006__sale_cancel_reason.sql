-- #128: preserve structured sale cancellation metadata on the business record.
-- Existing historical cancelled sales remain UNKNOWN rather than fabricating reasons.
CREATE TABLE sale_cancellation_details (
    sale_id UUID PRIMARY KEY REFERENCES sales(id),
    reason VARCHAR(40) NOT NULL CHECK (reason IN (
      'CUSTOMER_CHANGED_MIND','INSUFFICIENT_FUNDS','PRODUCT_MISMATCH',
      'DUPLICATE_ORDER','OTHER'
    )),
    comment VARCHAR(1000),
    cancelled_by VARCHAR(255) NOT NULL,
    cancelled_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT chk_sale_cancellation_comment CHECK (comment IS NULL OR length(comment) <= 1000)
);
