CREATE TABLE security_bootstrap_state (
                                          id SMALLINT PRIMARY KEY CHECK (id = 1),
                                          initialized BOOLEAN NOT NULL DEFAULT FALSE,
                                          initialized_at TIMESTAMPTZ
);

INSERT INTO security_bootstrap_state (
    id,
    initialized,
    initialized_at
)
VALUES (
           1,
           FALSE,
           NULL
       );