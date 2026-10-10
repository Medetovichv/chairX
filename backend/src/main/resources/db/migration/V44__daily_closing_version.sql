-- Optimistic revision for update requests; legacy closings begin at revision 0.
ALTER TABLE finance_daily_closings
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE finance_daily_closings
    ADD CONSTRAINT chk_finance_daily_closing_version CHECK (version >= 0);
