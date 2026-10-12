-- Stable idempotency key covers BOTH initial sale and linked delivery details.
CREATE TABLE sale_delivery_requests (
    idempotency_key UUID PRIMARY KEY,
    request_hash VARCHAR(64) NOT NULL CHECK (request_hash ~ '^[a-f0-9]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
