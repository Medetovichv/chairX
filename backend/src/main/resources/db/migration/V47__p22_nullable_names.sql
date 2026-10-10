-- P22-A: optional customer and delivery recipient names; preserve existing rows.
ALTER TABLE customers ALTER COLUMN full_name DROP NOT NULL;
ALTER TABLE customers DROP CONSTRAINT ck_customers_full_name_not_blank;
ALTER TABLE customers ADD CONSTRAINT ck_customers_full_name_not_blank
    CHECK (full_name IS NULL OR length(btrim(full_name)) > 0);

ALTER TABLE deliveries ALTER COLUMN recipient_name DROP NOT NULL;
ALTER TABLE deliveries DROP CONSTRAINT ck_deliveries_recipient_name_not_blank;
ALTER TABLE deliveries ADD CONSTRAINT ck_deliveries_recipient_name_not_blank
    CHECK (recipient_name IS NULL OR length(btrim(recipient_name)) > 0);
