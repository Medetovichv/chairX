ALTER TABLE purchase_items
ALTER COLUMN final_unit_cost TYPE NUMERIC(19, 2)
    USING ROUND(final_unit_cost, 2);