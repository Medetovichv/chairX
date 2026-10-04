CREATE TABLE products (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL CHECK (length(btrim(name)) > 0),
    description VARCHAR(4000),
    category VARCHAR(120),
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE product_variants (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id),
    name VARCHAR(200) NOT NULL CHECK (length(btrim(name)) > 0),
    sku VARCHAR(100),
    color VARCHAR(100),
    recommended_sale_price NUMERIC(19, 2) NOT NULL CHECK (recommended_sale_price >= 0),
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

-- Supports listing variants of a product in stable UUID order and the foreign key.
CREATE INDEX idx_product_variants_product_id_id ON product_variants(product_id, id);

CREATE TABLE audit_entries (
    id UUID PRIMARY KEY,
    entity_type VARCHAR(40) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(40) NOT NULL,
    actor VARCHAR(200) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    before_state JSONB,
    after_state JSONB NOT NULL
);
