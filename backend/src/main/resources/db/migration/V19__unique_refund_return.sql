CREATE UNIQUE INDEX uq_refunds_return_id
    ON refunds (return_id)
    WHERE return_id IS NOT NULL;