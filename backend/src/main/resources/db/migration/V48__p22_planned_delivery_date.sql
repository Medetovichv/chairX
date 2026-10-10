-- P22-B: optional planned delivery date; never backfill actual delivery dates.
ALTER TABLE deliveries ADD COLUMN planned_delivery_date DATE;
CREATE INDEX idx_deliveries_planned_date_status
    ON deliveries(planned_delivery_date, status, id)
    WHERE planned_delivery_date IS NOT NULL;
