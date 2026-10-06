CREATE SEQUENCE sale_number_seq
    START WITH 1
    INCREMENT BY 1
    NO CYCLE;

CREATE TABLE sales (
                       id UUID PRIMARY KEY,

                       sale_number VARCHAR(30) NOT NULL UNIQUE,

                       customer_id UUID,

                       fulfillment_type VARCHAR(30) NOT NULL,
                       status VARCHAR(30) NOT NULL,

                       created_by VARCHAR(200) NOT NULL,
                       created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),

                       fulfilled_by VARCHAR(200),
                       fulfilled_at TIMESTAMPTZ,

                       cancelled_by VARCHAR(200),
                       cancelled_at TIMESTAMPTZ,

                       CONSTRAINT fk_sales_customer
                           FOREIGN KEY (customer_id)
                               REFERENCES customers(id),

                       CONSTRAINT ck_sales_number_not_blank
                           CHECK (length(btrim(sale_number)) > 0),

                       CONSTRAINT ck_sales_fulfillment_type
                           CHECK (
                               fulfillment_type IN (
                                                    'SELF_PICKUP',
                                                    'CITY_DELIVERY',
                                                    'REGION_DELIVERY'
                                   )
                               ),

                       CONSTRAINT ck_sales_status
                           CHECK (
                               status IN (
                                          'CONFIRMED',
                                          'FULFILLED',
                                          'CANCELLED'
                                   )
                               ),

                       CONSTRAINT ck_sales_created_by_not_blank
                           CHECK (length(btrim(created_by)) > 0),

                       CONSTRAINT ck_sales_state
                           CHECK (
                               (
                                   status = 'CONFIRMED'
                                       AND fulfilled_at IS NULL
                                       AND fulfilled_by IS NULL
                                       AND cancelled_at IS NULL
                                       AND cancelled_by IS NULL
                                   )
                                   OR
                               (
                                   status = 'FULFILLED'
                                       AND fulfilled_at IS NOT NULL
                                       AND fulfilled_by IS NOT NULL
                                       AND length(btrim(fulfilled_by)) > 0
                                       AND cancelled_at IS NULL
                                       AND cancelled_by IS NULL
                                   )
                                   OR
                               (
                                   status = 'CANCELLED'
                                       AND cancelled_at IS NOT NULL
                                       AND cancelled_by IS NOT NULL
                                       AND length(btrim(cancelled_by)) > 0
                                       AND fulfilled_at IS NULL
                                       AND fulfilled_by IS NULL
                                   )
                               )
);

CREATE TABLE sale_items (
                            id UUID PRIMARY KEY,

                            sale_id UUID NOT NULL,
                            product_variant_id UUID NOT NULL,
                            warehouse_id UUID NOT NULL,

                            quantity BIGINT NOT NULL,
                            unit_sale_price NUMERIC(19, 2) NOT NULL,

                            CONSTRAINT fk_sale_items_sale
                                FOREIGN KEY (sale_id)
                                    REFERENCES sales(id),

                            CONSTRAINT fk_sale_items_product_variant
                                FOREIGN KEY (product_variant_id)
                                    REFERENCES product_variants(id),

                            CONSTRAINT fk_sale_items_warehouse
                                FOREIGN KEY (warehouse_id)
                                    REFERENCES warehouses(id),

                            CONSTRAINT ck_sale_items_quantity_positive
                                CHECK (quantity > 0),

                            CONSTRAINT ck_sale_items_price_non_negative
                                CHECK (unit_sale_price >= 0)
);

CREATE INDEX idx_sales_customer
    ON sales (customer_id)
    WHERE customer_id IS NOT NULL;

CREATE INDEX idx_sales_status_created_at
    ON sales (status, created_at DESC);

CREATE INDEX idx_sales_created_at
    ON sales (created_at DESC);

CREATE INDEX idx_sale_items_sale
    ON sale_items (sale_id);

CREATE INDEX idx_sale_items_variant
    ON sale_items (product_variant_id);

CREATE INDEX idx_sale_items_warehouse
    ON sale_items (warehouse_id);