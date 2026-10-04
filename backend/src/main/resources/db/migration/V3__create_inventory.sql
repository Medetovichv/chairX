CREATE TABLE inventory_balances (
    warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    product_variant_id UUID NOT NULL REFERENCES product_variants(id),
    on_hand BIGINT NOT NULL DEFAULT 0,
    reserved BIGINT NOT NULL DEFAULT 0,
    blocked BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (warehouse_id, product_variant_id),
    CONSTRAINT ck_inventory_on_hand CHECK (on_hand >= 0),
    CONSTRAINT ck_inventory_reserved CHECK (reserved >= 0 AND reserved <= on_hand),
    -- Subtraction avoids overflow of reserved + blocked for BIGINT quantities.
    CONSTRAINT ck_inventory_blocked CHECK (blocked >= 0 AND blocked <= on_hand - reserved)
);

CREATE TABLE stock_movements (
    id UUID PRIMARY KEY,
    operation_id UUID NOT NULL UNIQUE,
    warehouse_id UUID NOT NULL,
    product_variant_id UUID NOT NULL,
    movement_type VARCHAR(30) NOT NULL CHECK (movement_type IN (
        'PURCHASE_IN', 'SALE_OUT', 'RETURN_IN', 'TRANSFER_OUT', 'TRANSFER_IN',
        'WRITE_OFF', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT'
    )),
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    source_type VARCHAR(50) NOT NULL CHECK (source_type ~ '^[A-Z][A-Z0-9_]{0,49}$'),
    source_id UUID NOT NULL,
    actor VARCHAR(200),
    occurred_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (warehouse_id, product_variant_id)
        REFERENCES inventory_balances (warehouse_id, product_variant_id)
);

-- Paged history of one warehouse/variant, including deterministic tie-breaking.
CREATE INDEX idx_stock_movements_balance_time
    ON stock_movements (warehouse_id, product_variant_id, occurred_at, id);

CREATE FUNCTION reject_stock_movement_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Stock movements are append-only; create a correcting operation'
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER stock_movements_immutable
    BEFORE UPDATE OR DELETE ON stock_movements
    FOR EACH ROW EXECUTE FUNCTION reject_stock_movement_mutation();
