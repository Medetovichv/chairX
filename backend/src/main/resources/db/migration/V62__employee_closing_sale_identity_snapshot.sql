-- Historical personal reports retain the order-identifying details visible
-- at the moment the employee confirmed the day.
ALTER TABLE employee_daily_closing_sales
    ADD COLUMN products TEXT,
    ADD COLUMN contact_phone VARCHAR(50),
    ADD COLUMN delivery_address VARCHAR(500),
    ADD COLUMN fulfillment VARCHAR(30),
    ADD COLUMN delivery_status VARCHAR(30),
    ADD COLUMN payment_status VARCHAR(20);
