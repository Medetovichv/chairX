ALTER TABLE sale_delivery_requests ADD COLUMN generated_customer_id UUID REFERENCES customers(id);
