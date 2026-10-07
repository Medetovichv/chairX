CREATE TABLE deliveries (
                            id UUID PRIMARY KEY,
                            sale_id UUID NOT NULL UNIQUE,

                            status VARCHAR(30) NOT NULL,
                            recipient_name VARCHAR(200) NOT NULL,
                            recipient_phone VARCHAR(50) NOT NULL,
                            address VARCHAR(500) NOT NULL,
                            city_region VARCHAR(200),

                            delivery_cost NUMERIC(19,2) NOT NULL DEFAULT 0,

                            carrier_name VARCHAR(200),
                            tracking_number VARCHAR(200),
                            comment VARCHAR(2000),

                            created_by VARCHAR(200) NOT NULL,
                            created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),

                            dispatched_by VARCHAR(200),
                            dispatched_at TIMESTAMPTZ,

                            delivered_by VARCHAR(200),
                            delivered_at TIMESTAMPTZ,

                            failed_by VARCHAR(200),
                            failed_at TIMESTAMPTZ,
                            failure_reason VARCHAR(1000),

                            cancelled_by VARCHAR(200),
                            cancelled_at TIMESTAMPTZ,

                            returned_to_warehouse_by VARCHAR(200),
                            returned_to_warehouse_at TIMESTAMPTZ,
                            return_warehouse_id UUID,

                            CONSTRAINT fk_deliveries_sale
                                FOREIGN KEY (sale_id) REFERENCES sales(id),

                            CONSTRAINT fk_deliveries_return_warehouse
                                FOREIGN KEY (return_warehouse_id) REFERENCES warehouses(id),

                            CONSTRAINT ck_deliveries_status CHECK (
                                status IN (
                                           'READY',
                                           'IN_TRANSIT',
                                           'DELIVERED',
                                           'FAILED',
                                           'CANCELLED'
                                    )
                                ),

                            CONSTRAINT ck_deliveries_recipient_name_not_blank
                                CHECK (length(btrim(recipient_name)) > 0),

                            CONSTRAINT ck_deliveries_recipient_phone_not_blank
                                CHECK (length(btrim(recipient_phone)) > 0),

                            CONSTRAINT ck_deliveries_address_not_blank
                                CHECK (length(btrim(address)) > 0),

                            CONSTRAINT ck_deliveries_delivery_cost_non_negative
                                CHECK (delivery_cost >= 0),

                            CONSTRAINT ck_deliveries_created_by_not_blank
                                CHECK (length(btrim(created_by)) > 0),

                            CONSTRAINT ck_deliveries_return_state CHECK (
                                (
                                    returned_to_warehouse_at IS NULL
                                        AND returned_to_warehouse_by IS NULL
                                        AND return_warehouse_id IS NULL
                                    )
                                    OR
                                (
                                    returned_to_warehouse_at IS NOT NULL
                                        AND returned_to_warehouse_by IS NOT NULL
                                        AND length(btrim(returned_to_warehouse_by)) > 0
                                        AND return_warehouse_id IS NOT NULL
                                    )
                                )
);

CREATE INDEX idx_deliveries_status_created_at
    ON deliveries(status, created_at DESC);

CREATE INDEX idx_deliveries_recipient_phone
    ON deliveries(recipient_phone);

CREATE INDEX idx_deliveries_tracking_number
    ON deliveries(tracking_number)
    WHERE tracking_number IS NOT NULL;