-- Efficient read-only operational timeline for one sale. No audit data is modified.
CREATE INDEX idx_audit_entries_sale_timeline
    ON audit_entries (entity_id, occurred_at, id)
    WHERE entity_type = 'SALE';
