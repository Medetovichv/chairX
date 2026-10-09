-- Additive only: do not fabricate costs for legacy movements.
CREATE TABLE inventory_cost_restorations (
    return_movement_id UUID NOT NULL REFERENCES stock_movements(id),
    original_allocation_id UUID NOT NULL REFERENCES inventory_cost_allocations(id),
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    amount NUMERIC(38,2) NOT NULL CHECK (amount >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (return_movement_id, original_allocation_id)
);
CREATE INDEX idx_cost_restorations_original ON inventory_cost_restorations(original_allocation_id);
CREATE TABLE inventory_cost_write_offs (
    stock_movement_id UUID PRIMARY KEY REFERENCES stock_movements(id),
    purchase_receipt_item_id UUID REFERENCES purchase_receipt_items(id)
);
CREATE FUNCTION check_cost_restoration() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    original inventory_cost_allocations%ROWTYPE;
    returned stock_movements%ROWTYPE;
    source stock_movements%ROWTYPE;
    used_quantity NUMERIC;
    used_amount NUMERIC;
BEGIN
    SELECT * INTO STRICT original FROM inventory_cost_allocations WHERE id=NEW.original_allocation_id;
    SELECT * INTO STRICT source FROM stock_movements WHERE id=original.stock_movement_id FOR UPDATE;
    SELECT * INTO STRICT returned FROM stock_movements WHERE id=NEW.return_movement_id;
    IF source.movement_type <> 'SALE_OUT' OR returned.movement_type <> 'RETURN_IN'
        OR source.product_variant_id <> returned.product_variant_id THEN
        RAISE EXCEPTION 'Invalid restoration origin' USING ERRCODE='23514';
    END IF;
    SELECT COALESCE(sum(quantity),0),COALESCE(sum(amount),0) INTO used_quantity,used_amount
        FROM inventory_cost_restorations WHERE original_allocation_id=original.id;
    IF used_quantity+NEW.quantity > original.quantity OR used_amount+NEW.amount > original.allocated_cost THEN
        RAISE EXCEPTION 'Restoration exceeds original allocation' USING ERRCODE='23514';
    END IF;
    IF used_quantity+NEW.quantity = original.quantity AND used_amount+NEW.amount <> original.allocated_cost THEN
        RAISE EXCEPTION 'Full restoration must conserve original cost' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER cost_restoration_limit BEFORE INSERT ON inventory_cost_restorations
    FOR EACH ROW EXECUTE FUNCTION check_cost_restoration();
CREATE FUNCTION check_complete_cost_restoration() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    restored_quantity NUMERIC;
    restored_amount NUMERIC;
    physical_quantity BIGINT;
    layer_cost NUMERIC;
    layer_quantity BIGINT;
BEGIN
    SELECT sum(quantity),sum(amount) INTO restored_quantity,restored_amount
        FROM inventory_cost_restorations WHERE return_movement_id=NEW.return_movement_id;
    SELECT quantity INTO STRICT physical_quantity FROM stock_movements WHERE id=NEW.return_movement_id;
    SELECT total_cost,quantity_received INTO layer_cost,layer_quantity
        FROM inventory_cost_layers WHERE source_movement_id=NEW.return_movement_id;
    IF restored_quantity <> physical_quantity OR layer_quantity IS DISTINCT FROM physical_quantity
        OR layer_cost IS DISTINCT FROM restored_amount THEN
        RAISE EXCEPTION 'Incomplete return cost posting' USING ERRCODE='23514';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER cost_restoration_complete AFTER INSERT ON inventory_cost_restorations
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_complete_cost_restoration();
CREATE TRIGGER cost_allocations_immutable BEFORE UPDATE OR DELETE ON inventory_cost_allocations
    FOR EACH ROW EXECUTE FUNCTION reject_stock_movement_mutation();
CREATE TRIGGER cost_restorations_immutable BEFORE UPDATE OR DELETE ON inventory_cost_restorations
    FOR EACH ROW EXECUTE FUNCTION reject_stock_movement_mutation();
CREATE TRIGGER cost_write_offs_immutable BEFORE UPDATE OR DELETE ON inventory_cost_write_offs
    FOR EACH ROW EXECUTE FUNCTION reject_stock_movement_mutation();
CREATE FUNCTION protect_cost_layer_origin() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'Cost layers cannot be deleted' USING ERRCODE='55000';
    END IF;
    IF ROW(NEW.id,NEW.warehouse_id,NEW.product_variant_id,NEW.source_movement_id,
           NEW.quantity_received,NEW.total_cost,NEW.received_at)
        IS DISTINCT FROM ROW(OLD.id,OLD.warehouse_id,OLD.product_variant_id,OLD.source_movement_id,
           OLD.quantity_received,OLD.total_cost,OLD.received_at) THEN
        RAISE EXCEPTION 'Cost layer origin is immutable' USING ERRCODE='55000';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER cost_layer_origin_immutable BEFORE UPDATE OR DELETE ON inventory_cost_layers
    FOR EACH ROW EXECUTE FUNCTION protect_cost_layer_origin();
-- Read-only diagnostics. A result requires investigation, not a zero-price backfill.
CREATE VIEW inventory_unvalued_movements AS
SELECT m.* FROM stock_movements m
WHERE (m.movement_type IN ('PURCHASE_IN','ADJUSTMENT_IN','RETURN_IN','TRANSFER_IN') AND NOT EXISTS (
          SELECT 1 FROM inventory_cost_layers l WHERE l.source_movement_id=m.id))
   OR (m.movement_type IN ('SALE_OUT','WRITE_OFF','ADJUSTMENT_OUT','TRANSFER_OUT') AND NOT EXISTS (
          SELECT 1 FROM inventory_cost_allocations a WHERE a.stock_movement_id=m.id));
CREATE VIEW inventory_cost_balance_discrepancies AS
WITH costs AS (
    SELECT warehouse_id,product_variant_id,sum(quantity_remaining) AS priced_quantity,
           sum(remaining_cost) AS remaining_cost
    FROM inventory_cost_layers GROUP BY warehouse_id,product_variant_id
)
SELECT COALESCE(b.warehouse_id,c.warehouse_id) AS warehouse_id,
       COALESCE(b.product_variant_id,c.product_variant_id) AS product_variant_id,
       COALESCE(b.on_hand,0) AS on_hand,COALESCE(c.priced_quantity,0) AS priced_quantity,
       COALESCE(c.remaining_cost,0) AS remaining_cost
FROM inventory_balances b FULL JOIN costs c USING (warehouse_id,product_variant_id)
WHERE COALESCE(b.on_hand,0) <> COALESCE(c.priced_quantity,0);
