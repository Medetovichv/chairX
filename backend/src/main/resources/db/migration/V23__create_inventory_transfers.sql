CREATE TABLE inventory_transfers (
                                     id UUID PRIMARY KEY,

                                     source_warehouse_id UUID NOT NULL
                                         REFERENCES warehouses(id),

                                     destination_warehouse_id UUID NOT NULL
                                         REFERENCES warehouses(id),

                                     product_variant_id UUID NOT NULL
                                         REFERENCES product_variants(id),

                                     quantity BIGINT NOT NULL CHECK (quantity > 0),

                                     actor VARCHAR(200),

                                     out_movement_id UUID UNIQUE
                                         REFERENCES stock_movements(id),

                                     in_movement_id UUID UNIQUE
                                         REFERENCES stock_movements(id),

                                     created_at TIMESTAMPTZ NOT NULL
                                         DEFAULT clock_timestamp(),

                                     CONSTRAINT chk_transfer_different_warehouses
                                         CHECK (source_warehouse_id <> destination_warehouse_id),

                                     CONSTRAINT chk_transfer_movements_together
                                         CHECK (
                                             (out_movement_id IS NULL AND in_movement_id IS NULL)
                                                 OR
                                             (out_movement_id IS NOT NULL AND in_movement_id IS NOT NULL)
                                             )
);

CREATE TABLE inventory_transfer_cost_origins (
                                                 transfer_id UUID NOT NULL
                                                     REFERENCES inventory_transfers(id),

                                                 source_allocation_id UUID NOT NULL UNIQUE
                                                     REFERENCES inventory_cost_allocations(id),

                                                 quantity BIGINT NOT NULL CHECK (quantity > 0),

                                                 amount NUMERIC(38, 2) NOT NULL CHECK (amount >= 0),

                                                 PRIMARY KEY (transfer_id, source_allocation_id)
);

CREATE INDEX idx_inventory_transfers_source
    ON inventory_transfers(source_warehouse_id, created_at);

CREATE INDEX idx_inventory_transfers_destination
    ON inventory_transfers(destination_warehouse_id, created_at);

CREATE FUNCTION protect_completed_inventory_transfer()
    RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Inventory transfers cannot be deleted';
END IF;

    IF OLD.out_movement_id IS NOT NULL
       OR OLD.in_movement_id IS NOT NULL THEN
        RAISE EXCEPTION 'Completed inventory transfers are immutable';
END IF;

    IF ROW(
        NEW.id,
        NEW.source_warehouse_id,
        NEW.destination_warehouse_id,
        NEW.product_variant_id,
        NEW.quantity,
        NEW.actor,
        NEW.created_at
    ) IS DISTINCT FROM ROW(
        OLD.id,
        OLD.source_warehouse_id,
        OLD.destination_warehouse_id,
        OLD.product_variant_id,
        OLD.quantity,
        OLD.actor,
        OLD.created_at
    ) THEN
        RAISE EXCEPTION 'Inventory transfer identity is immutable';
END IF;

RETURN NEW;
END;
$$;

CREATE TRIGGER inventory_transfers_protect
    BEFORE UPDATE OR DELETE ON inventory_transfers
FOR EACH ROW
EXECUTE FUNCTION protect_completed_inventory_transfer();

CREATE TRIGGER inventory_transfer_cost_origins_immutable
    BEFORE UPDATE OR DELETE ON inventory_transfer_cost_origins
FOR EACH ROW
EXECUTE FUNCTION reject_stock_movement_mutation();