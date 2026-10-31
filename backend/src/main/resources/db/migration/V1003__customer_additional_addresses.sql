CREATE TABLE customer_addresses (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    address VARCHAR(500) NOT NULL,
    city_region VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT customer_addresses_nonblank CHECK (length(btrim(address)) > 0),
    CONSTRAINT customer_addresses_unique UNIQUE (customer_id, address, city_region)
);
CREATE INDEX idx_customer_addresses_customer ON customer_addresses(customer_id, created_at);
