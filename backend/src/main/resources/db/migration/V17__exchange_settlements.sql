CREATE TABLE exchange_settlements (
                                      id UUID PRIMARY KEY,

                                      exchange_id UUID NOT NULL
                                          REFERENCES exchanges(id),

                                      direction VARCHAR(20) NOT NULL,

                                      method VARCHAR(30) NOT NULL,

                                      amount NUMERIC(19, 2) NOT NULL,

                                      reference VARCHAR(200),

                                      idempotency_key UUID NOT NULL,

                                      created_by VARCHAR(200) NOT NULL,
                                      created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                      CONSTRAINT uq_exchange_settlement_idempotency
                                          UNIQUE (idempotency_key),

                                      CONSTRAINT chk_exchange_settlement_direction
                                          CHECK (direction IN ('IN', 'OUT')),

                                      CONSTRAINT chk_exchange_settlement_method
                                          CHECK (method IN ('CASH', 'TRANSFER')),

                                      CONSTRAINT chk_exchange_settlement_amount
                                          CHECK (
                                              amount > 0
                                                  AND amount = trunc(amount)
                                              )
);

CREATE INDEX idx_exchange_settlements_exchange
    ON exchange_settlements(exchange_id);