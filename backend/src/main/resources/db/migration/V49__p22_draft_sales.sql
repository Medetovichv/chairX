-- P22-C: sale drafts are operational records and have no reservation or financial postings.
ALTER TABLE sales ADD COLUMN comment VARCHAR(2000);
ALTER TABLE sales ALTER COLUMN fulfillment_type DROP NOT NULL;
ALTER TABLE sales DROP CONSTRAINT ck_sales_status;
ALTER TABLE sales ADD CONSTRAINT ck_sales_status
    CHECK (status IN ('DRAFT', 'CONFIRMED', 'FULFILLED', 'CANCELLED'));
ALTER TABLE sales DROP CONSTRAINT ck_sales_state;
ALTER TABLE sales ADD CONSTRAINT ck_sales_state CHECK (
    (status IN ('DRAFT', 'CONFIRMED')
        AND fulfilled_at IS NULL AND fulfilled_by IS NULL
        AND cancelled_at IS NULL AND cancelled_by IS NULL)
    OR (status = 'FULFILLED'
        AND fulfilled_at IS NOT NULL AND fulfilled_by IS NOT NULL
        AND length(btrim(fulfilled_by)) > 0
        AND cancelled_at IS NULL AND cancelled_by IS NULL)
    OR (status = 'CANCELLED'
        AND cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL
        AND length(btrim(cancelled_by)) > 0
        AND fulfilled_at IS NULL AND fulfilled_by IS NULL)
);
ALTER TABLE sales ADD CONSTRAINT ck_sales_fulfillment_required_when_confirmed
    CHECK (status IN ('DRAFT', 'CANCELLED') OR fulfillment_type IS NOT NULL);
