CREATE TABLE warehouses (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL CHECK (length(btrim(name)) > 0),
    code VARCHAR(50) NOT NULL,
    address VARCHAR(1000),
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_warehouses_code UNIQUE (code),
    CONSTRAINT ck_warehouses_code CHECK (code ~ '^[A-Z0-9][A-Z0-9_-]{0,49}$')
);

-- Flyway applies these rows once; future startup must not overwrite user edits.
INSERT INTO warehouses (id, name, code, active, created_at, updated_at) VALUES
    ('30943c33-a5d6-4efd-b795-c46b6ead942a', 'Домашний склад', 'HOME', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('c0f54da4-468a-4ac9-a0b4-aed1e441025f', 'Офисный склад', 'OFFICE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
