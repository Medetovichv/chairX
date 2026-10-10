-- P21-I: preserve the originally signed daily-closing snapshot.
-- Late, real financial movements may revise the calculated ledger expectation
-- for several closed reports without rewriting their original observations.
-- Every change also creates an audited report revision in application code.
CREATE TABLE finance_daily_closing_adjustments (
    closing_id UUID NOT NULL,
    account_code VARCHAR(10) NOT NULL,
    expected_balance NUMERIC(19,2) NOT NULL,
    adjusted_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    adjusted_by VARCHAR(200) NOT NULL,
    PRIMARY KEY (closing_id, account_code),
    FOREIGN KEY (closing_id, account_code)
        REFERENCES finance_daily_closing_accounts (closing_id, account_code)
        ON DELETE CASCADE,
    CONSTRAINT chk_closing_adjustment_expected
        CHECK (expected_balance >= 0 AND expected_balance = trunc(expected_balance)),
    CONSTRAINT chk_closing_adjustment_actor
        CHECK (length(trim(adjusted_by)) > 0)
);

COMMENT ON TABLE finance_daily_closing_adjustments IS
  'Recomputed ledger expectations after audited historical postings; original finance_daily_closing_accounts.expected_balance is immutable.';
