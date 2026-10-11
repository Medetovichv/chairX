-- Immutable staff discussions scoped to a sale. No WhatsApp or social integration.
CREATE TABLE sale_comments (
    id UUID PRIMARY KEY,
    sale_id UUID NOT NULL REFERENCES sales(id),
    idempotency_key UUID NOT NULL UNIQUE,
    author VARCHAR(200) NOT NULL CHECK (length(btrim(author)) > 0),
    text VARCHAR(2000) NOT NULL CHECK (length(btrim(text)) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX idx_sale_comments_sale_time ON sale_comments (sale_id,created_at DESC,id DESC);

INSERT INTO security_permissions(code,description)
VALUES ('SALES_COMMENT_CREATE','Добавление комментариев к продажам')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions(role_id,permission_code)
SELECT id,'SALES_COMMENT_CREATE' FROM security_roles
WHERE system_role=TRUE AND code IN ('ADMIN','MANAGER','EMPLOYEE')
ON CONFLICT (role_id,permission_code) DO NOTHING;
