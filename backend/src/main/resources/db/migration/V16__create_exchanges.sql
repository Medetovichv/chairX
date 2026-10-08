CREATE TABLE exchanges (
                           id UUID PRIMARY KEY,

                           original_sale_id UUID NOT NULL
                               REFERENCES sales(id),

                           new_sale_id UUID NOT NULL
                               REFERENCES sales(id),

                           return_id UUID NOT NULL
                               REFERENCES returns(id),

                           status VARCHAR(30) NOT NULL,

                           returned_value NUMERIC(19, 2) NOT NULL,
                           new_sale_total NUMERIC(19, 2) NOT NULL,

                           credit_applied NUMERIC(19, 2) NOT NULL,
                           additional_payment_due NUMERIC(19, 2) NOT NULL,
                           refund_due NUMERIC(19, 2) NOT NULL,

                           idempotency_key UUID NOT NULL,
                           request_fingerprint VARCHAR(64) NOT NULL,

                           created_by VARCHAR(200) NOT NULL,
                           created_at TIMESTAMPTZ NOT NULL,

                           completed_by VARCHAR(200),
                           completed_at TIMESTAMPTZ,

                           CONSTRAINT uq_exchanges_idempotency
                               UNIQUE (idempotency_key),

                           CONSTRAINT uq_exchanges_new_sale
                               UNIQUE (new_sale_id),

                           CONSTRAINT uq_exchanges_return
                               UNIQUE (return_id),

                           CONSTRAINT chk_exchanges_different_sales
                               CHECK (original_sale_id <> new_sale_id),

                           CONSTRAINT chk_exchanges_status
                               CHECK (status IN ('PENDING_SETTLEMENT', 'COMPLETED')),

                           CONSTRAINT chk_exchanges_amounts
                               CHECK (
                                   returned_value >= 0
                                       AND new_sale_total >= 0
                                       AND credit_applied >= 0
                                       AND additional_payment_due >= 0
                                       AND refund_due >= 0
                                       AND returned_value = trunc(returned_value)
                                       AND new_sale_total = trunc(new_sale_total)
                                       AND credit_applied = trunc(credit_applied)
                                       AND additional_payment_due = trunc(additional_payment_due)
                                       AND refund_due = trunc(refund_due)
                                   ),

                           CONSTRAINT chk_exchanges_credit
                               CHECK (
                                   credit_applied = LEAST(returned_value, new_sale_total)
                                       AND additional_payment_due = new_sale_total - credit_applied
                                       AND refund_due = returned_value - credit_applied
                                   ),

                           CONSTRAINT chk_exchanges_completion
                               CHECK (
                                   (
                                       status = 'PENDING_SETTLEMENT'
                                           AND completed_by IS NULL
                                           AND completed_at IS NULL
                                       )
                                       OR
                                   (
                                       status = 'COMPLETED'
                                           AND completed_by IS NOT NULL
                                           AND completed_at IS NOT NULL
                                       )
                                   )
);

CREATE INDEX idx_exchanges_original_sale
    ON exchanges(original_sale_id);

CREATE INDEX idx_exchanges_created_at
    ON exchanges(created_at);