-- #107: Freeze separately collected goods and delivery amounts in new employee closings.
-- Historical snapshots have only their immutable total; never reconstruct their
-- component prices from today's (mutable) deliveries.delivery_cost.
ALTER TABLE employee_daily_closing_sales
    ADD COLUMN goods_total NUMERIC(19,2),
    ADD COLUMN delivery_total NUMERIC(19,2);

ALTER TABLE employee_daily_closing_sales
    ADD CONSTRAINT chk_employee_close_sale_components
    CHECK (
        (goods_total IS NULL AND delivery_total IS NULL)
        OR (
            goods_total IS NOT NULL AND delivery_total IS NOT NULL
            AND goods_total >= 0 AND delivery_total >= 0
            AND goods_total + delivery_total = total
        )
    );

COMMENT ON COLUMN employee_daily_closing_sales.goods_total IS
    'Goods amount frozen at closing; NULL means unavailable for a historical legacy snapshot';
COMMENT ON COLUMN employee_daily_closing_sales.delivery_total IS
    'Customer delivery charge frozen at closing; NULL means unavailable for a historical legacy snapshot';
