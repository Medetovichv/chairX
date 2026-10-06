CREATE TABLE customers (
                           id UUID PRIMARY KEY,

                           full_name VARCHAR(200) NOT NULL,
                           phone VARCHAR(50),
                           secondary_phone VARCHAR(50),
                           whatsapp_phone VARCHAR(50),
                           instagram_username VARCHAR(100),

                           address VARCHAR(500),
                           city_region VARCHAR(200),

                           comment VARCHAR(2000),

                           active BOOLEAN NOT NULL DEFAULT TRUE,

                           created_at TIMESTAMPTZ NOT NULL,
                           updated_at TIMESTAMPTZ NOT NULL,

                           CONSTRAINT ck_customers_full_name_not_blank
                               CHECK (length(btrim(full_name)) > 0),

                           CONSTRAINT ck_customers_phone_not_blank
                               CHECK (phone IS NULL OR length(btrim(phone)) > 0),

                           CONSTRAINT ck_customers_secondary_phone_not_blank
                               CHECK (
                                   secondary_phone IS NULL
                                       OR length(btrim(secondary_phone)) > 0
                                   ),

                           CONSTRAINT ck_customers_whatsapp_phone_not_blank
                               CHECK (
                                   whatsapp_phone IS NULL
                                       OR length(btrim(whatsapp_phone)) > 0
                                   ),

                           CONSTRAINT ck_customers_instagram_not_blank
                               CHECK (
                                   instagram_username IS NULL
                                       OR length(btrim(instagram_username)) > 0
                                   )
);

CREATE INDEX idx_customers_full_name
    ON customers (lower(full_name));

CREATE INDEX idx_customers_phone
    ON customers (phone)
    WHERE phone IS NOT NULL;

CREATE INDEX idx_customers_active
    ON customers (active);