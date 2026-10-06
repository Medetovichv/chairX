CREATE TABLE suppliers (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL CHECK (length(btrim(name)) > 0),
    contact_information VARCHAR(1000),
    comment VARCHAR(4000),
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE purchases (
    id UUID PRIMARY KEY,
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    status VARCHAR(30) NOT NULL CHECK (status IN ('DRAFT','CONFIRMED','PARTIALLY_RECEIVED','RECEIVED','CANCELLED')),
    cargo_cost NUMERIC(19,2) CHECK (cargo_cost >= 0),
    cargo_allocation_method VARCHAR(20) NOT NULL CHECK (cargo_allocation_method = 'BY_QUANTITY'),
    costs_locked_at TIMESTAMPTZ,
    confirmed_at TIMESTAMPTZ,
    comment VARCHAR(4000),
    created_by VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CHECK (costs_locked_at IS NULL OR cargo_cost IS NOT NULL)
);

CREATE TABLE purchase_items (
    id UUID PRIMARY KEY,
    purchase_id UUID NOT NULL REFERENCES purchases(id),
    product_variant_id UUID NOT NULL REFERENCES product_variants(id),
    line_number INTEGER NOT NULL CHECK (line_number > 0),
    ordered_quantity BIGINT NOT NULL CHECK (ordered_quantity > 0),
    received_quantity BIGINT NOT NULL DEFAULT 0 CHECK (received_quantity >= 0 AND received_quantity <= ordered_quantity),
    purchase_unit_cost NUMERIC(19,2) NOT NULL CHECK (purchase_unit_cost >= 0),
    allocated_cargo_cost NUMERIC(19,2) CHECK (allocated_cargo_cost >= 0),
    final_unit_cost NUMERIC(25,6) CHECK (final_unit_cost >= 0),
    UNIQUE (purchase_id, id),
    UNIQUE (purchase_id, line_number),
    CHECK ((allocated_cargo_cost IS NULL) = (final_unit_cost IS NULL))
);

CREATE TABLE purchase_receipts (
    id UUID PRIMARY KEY,
    purchase_id UUID NOT NULL REFERENCES purchases(id),
    warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    idempotency_key UUID NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    posted_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(200) NOT NULL,
    comment VARCHAR(4000),
    UNIQUE (purchase_id, id),
    UNIQUE (purchase_id, idempotency_key)
);

CREATE TABLE purchase_receipt_items (
    id UUID PRIMARY KEY,
    purchase_id UUID NOT NULL,
    receipt_id UUID NOT NULL,
    purchase_item_id UUID NOT NULL,
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    allocated_cargo_cost NUMERIC(19,2) NOT NULL CHECK (allocated_cargo_cost >= 0),
    total_cost NUMERIC(38,2) NOT NULL CHECK (total_cost >= 0),
    FOREIGN KEY (purchase_id, receipt_id) REFERENCES purchase_receipts(purchase_id, id),
    FOREIGN KEY (purchase_id, purchase_item_id) REFERENCES purchase_items(purchase_id, id),
    UNIQUE (receipt_id, purchase_item_id)
);

-- Supports pagination of a purchase's receipt history; the other access paths use PK/UNIQUE indexes.
CREATE INDEX idx_purchase_receipts_purchase_time ON purchase_receipts(purchase_id, posted_at, id);

CREATE FUNCTION reject_purchase_receipt_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Posted purchase receipts are immutable' USING ERRCODE = '55000';
END;
$$;
CREATE TRIGGER purchase_receipts_immutable BEFORE UPDATE OR DELETE ON purchase_receipts
    FOR EACH ROW EXECUTE FUNCTION reject_purchase_receipt_mutation();
CREATE TRIGGER purchase_receipt_items_immutable BEFORE UPDATE OR DELETE ON purchase_receipt_items
    FOR EACH ROW EXECUTE FUNCTION reject_purchase_receipt_mutation();
