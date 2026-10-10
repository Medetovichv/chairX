-- P21: each grant is immutable except for explicit revocation.
-- A report row lock serializes grants for the same business date; the
-- application must refuse extension of any still-active grant.
CREATE TABLE finance_daily_closing_unlocks (
    id UUID PRIMARY KEY,
    closing_id UUID NOT NULL REFERENCES finance_daily_closings(id),
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
    ON finance_daily_closing_unlocks(closing_id, unlocked_at DESC);
