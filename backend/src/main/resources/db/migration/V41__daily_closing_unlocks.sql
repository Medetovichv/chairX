-- P21: grants bind to a report date even when an overdue report has not yet
-- been created. A dedicated row serializes unlock and report creation.
CREATE TABLE finance_daily_closing_access_locks (
    business_date DATE PRIMARY KEY
);

CREATE TABLE finance_daily_closing_unlocks (
    id UUID PRIMARY KEY,
    business_date DATE NOT NULL REFERENCES finance_daily_closing_access_locks(business_date),
    unlocked_by VARCHAR(100) NOT NULL,
    unlocked_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoked_by VARCHAR(100),
    CONSTRAINT chk_closing_unlock_actor CHECK (length(trim(unlocked_by)) > 0),
    CONSTRAINT chk_closing_unlock_reason CHECK (length(trim(reason)) > 0),
    CONSTRAINT chk_closing_unlock_duration CHECK (expires_at > unlocked_at AND expires_at <= unlocked_at + interval '60 minutes'),
    CONSTRAINT chk_closing_unlock_revoke CHECK (
        (revoked_at IS NULL AND revoked_by IS NULL) OR
        (revoked_at IS NOT NULL AND revoked_by IS NOT NULL AND length(trim(revoked_by)) > 0)
    )
);

CREATE INDEX idx_closing_unlocks_latest
    ON finance_daily_closing_unlocks(business_date, unlocked_at DESC);
