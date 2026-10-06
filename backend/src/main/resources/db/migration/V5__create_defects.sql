CREATE TABLE defects (
                         id UUID PRIMARY KEY,

                         warehouse_id UUID NOT NULL,
                         product_variant_id UUID NOT NULL,

                         supplier_id UUID,
                         purchase_receipt_item_id UUID,

                         quantity BIGINT NOT NULL,

                         status VARCHAR(30) NOT NULL,

                         description VARCHAR(2000) NOT NULL,
                         resolution_note VARCHAR(2000),

                         created_by VARCHAR(200),
                         created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),

                         resolved_by VARCHAR(200),
                         resolved_at TIMESTAMPTZ,

                         CONSTRAINT fk_defects_warehouse
                             FOREIGN KEY (warehouse_id)
                                 REFERENCES warehouses(id),

                         CONSTRAINT fk_defects_product_variant
                             FOREIGN KEY (product_variant_id)
                                 REFERENCES product_variants(id),

                         CONSTRAINT fk_defects_supplier
                             FOREIGN KEY (supplier_id)
                                 REFERENCES suppliers(id),

                         CONSTRAINT fk_defects_purchase_receipt_item
                             FOREIGN KEY (purchase_receipt_item_id)
                                 REFERENCES purchase_receipt_items(id),

                         CONSTRAINT ck_defects_quantity_positive
                             CHECK (quantity > 0),

                         CONSTRAINT ck_defects_status
                             CHECK (status IN (
                                               'OPEN',
                                               'WAITING_PARTS',
                                               'RESOLVED',
                                               'WRITTEN_OFF'
                                 )),

                         CONSTRAINT ck_defects_description_not_blank
                             CHECK (length(trim(description)) > 0),

                         CONSTRAINT ck_defects_resolution
                             CHECK (
                                 (
                                     status IN ('OPEN', 'WAITING_PARTS')
                                         AND resolved_at IS NULL
                                         AND resolved_by IS NULL
                                     )
                                     OR
                                 (
                                     status IN ('RESOLVED', 'WRITTEN_OFF')
                                         AND resolved_at IS NOT NULL
                                     )
                                 )
);

CREATE INDEX idx_defects_warehouse_status
    ON defects (warehouse_id, status);

CREATE INDEX idx_defects_variant_status
    ON defects (product_variant_id, status);

CREATE INDEX idx_defects_receipt_item
    ON defects (purchase_receipt_item_id)
    WHERE purchase_receipt_item_id IS NOT NULL;