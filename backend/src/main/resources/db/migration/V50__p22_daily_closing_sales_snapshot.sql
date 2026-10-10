-- P22-E: standalone immutable operational sale snapshots for P21 daily closings.
-- Historic P21 closings intentionally have no marker, even when they have zero sales.
CREATE TABLE finance_daily_closing_sales_snapshots(
    closing_id UUID PRIMARY KEY REFERENCES finance_daily_closings(id) ON DELETE CASCADE,
    captured_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE finance_daily_closing_sales(
    closing_id UUID NOT NULL REFERENCES finance_daily_closing_sales_snapshots(closing_id) ON DELETE CASCADE,
    sale_id UUID NOT NULL, -- snapshot must remain independent of mutable/deletable operational data
    sale_number VARCHAR(30) NOT NULL,
    customer_name VARCHAR(200),
    phone VARCHAR(50),
    products TEXT,
    quantity BIGINT NOT NULL CHECK(quantity >= 0),
    address VARCHAR(500),
    fulfillment_type VARCHAR(30) NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    total NUMERIC(25,2) NOT NULL CHECK(total >= 0),
    payment_status VARCHAR(16) NOT NULL,
    PRIMARY KEY(closing_id,sale_id)
);
CREATE INDEX idx_closing_sales_sale ON finance_daily_closing_sales(sale_id);
CREATE INDEX idx_sales_fulfilled_at ON sales(fulfilled_at) WHERE status='FULFILLED';
CREATE INDEX idx_deliveries_delivered_at ON deliveries(delivered_at) WHERE status='DELIVERED';
