-- Партии себестоимости.
-- Каждая партия относится к конкретному складу и варианту товара.

CREATE TABLE inventory_cost_layers (
                                       id UUID PRIMARY KEY,

                                       warehouse_id UUID NOT NULL
                                           REFERENCES warehouses(id),

                                       product_variant_id UUID NOT NULL
                                           REFERENCES product_variants(id),

                                       source_movement_id UUID NOT NULL UNIQUE
                                           REFERENCES stock_movements(id),

                                       quantity_received BIGINT NOT NULL,
                                       quantity_remaining BIGINT NOT NULL,

                                       total_cost NUMERIC(38, 2) NOT NULL,
                                       remaining_cost NUMERIC(38, 2) NOT NULL,

                                       received_at TIMESTAMPTZ NOT NULL,

                                       CONSTRAINT chk_cost_layer_quantity
                                           CHECK (
                                               quantity_received > 0
                                                   AND quantity_remaining >= 0
                                                   AND quantity_remaining <= quantity_received
                                               ),

                                       CONSTRAINT chk_cost_layer_amount
                                           CHECK (
                                               total_cost >= 0
                                                   AND remaining_cost >= 0
                                                   AND remaining_cost <= total_cost
                                               ),

                                       CONSTRAINT chk_cost_layer_empty
                                           CHECK (
                                               (quantity_remaining = 0 AND remaining_cost = 0)
                                                   OR
                                               (quantity_remaining > 0)
                                               )
);

CREATE INDEX idx_inventory_cost_layers_fifo
    ON inventory_cost_layers (
                              warehouse_id,
                              product_variant_id,
                              received_at,
                              id
        )
    WHERE quantity_remaining > 0;


-- Фиксируем, какие партии использованы при списании.
-- Одна продажа может списывать несколько партий.

CREATE TABLE inventory_cost_allocations (
                                            id UUID PRIMARY KEY,

                                            stock_movement_id UUID NOT NULL
                                                REFERENCES stock_movements(id),

                                            cost_layer_id UUID NOT NULL
                                                REFERENCES inventory_cost_layers(id),

                                            quantity BIGINT NOT NULL
                                                CHECK (quantity > 0),

                                            allocated_cost NUMERIC(38, 2) NOT NULL
                                                CHECK (allocated_cost >= 0),

                                            created_at TIMESTAMPTZ NOT NULL
                                                DEFAULT CURRENT_TIMESTAMP,

                                            CONSTRAINT uq_inventory_cost_allocation
                                                UNIQUE (stock_movement_id, cost_layer_id)
);

CREATE INDEX idx_inventory_cost_allocations_layer
    ON inventory_cost_allocations(cost_layer_id);


-- История операций с себестоимостью.
-- Исторические записи не изменяются.

CREATE TABLE inventory_cost_movements (
                                          id UUID PRIMARY KEY,

                                          cost_layer_id UUID NOT NULL
                                              REFERENCES inventory_cost_layers(id),

                                          stock_movement_id UUID NOT NULL
                                              REFERENCES stock_movements(id),

                                          direction VARCHAR(10) NOT NULL
                                              CHECK (direction IN ('IN', 'OUT')),

                                          quantity BIGINT NOT NULL
                                              CHECK (quantity > 0),

                                          amount NUMERIC(38, 2) NOT NULL
                                              CHECK (amount >= 0),

                                          created_at TIMESTAMPTZ NOT NULL
                                              DEFAULT CURRENT_TIMESTAMP,

                                          CONSTRAINT uq_inventory_cost_movement
                                              UNIQUE (cost_layer_id, stock_movement_id, direction)
);

CREATE INDEX idx_inventory_cost_movements_layer
    ON inventory_cost_movements(cost_layer_id, created_at);


-- Историю себестоимости нельзя переписывать.

CREATE TRIGGER inventory_cost_movements_immutable
    BEFORE UPDATE OR DELETE
ON inventory_cost_movements
    FOR EACH ROW
    EXECUTE FUNCTION reject_stock_movement_mutation();