-- Preserve legacy cancellations: new columns are nullable for historical rows.
ALTER TABLE sales
    ADD COLUMN cancellation_reason VARCHAR(64),
    ADD COLUMN cancellation_comment VARCHAR(1000);

ALTER TABLE sales
    ADD CONSTRAINT sales_cancellation_reason_allowed CHECK (
        cancellation_reason IS NULL OR cancellation_reason IN (
            'CUSTOMER_CHANGED_MIND', 'PRODUCT_NOT_SUITABLE',
            'PRICE_NOT_ACCEPTABLE', 'DUPLICATE_ORDER',
            'DELIVERY_UNAVAILABLE', 'OTHER'
        )
    );

ALTER TABLE sales
    ADD CONSTRAINT sales_cancellation_metadata_only_when_cancelled CHECK (
        cancellation_reason IS NULL OR status = 'CANCELLED'
    );
