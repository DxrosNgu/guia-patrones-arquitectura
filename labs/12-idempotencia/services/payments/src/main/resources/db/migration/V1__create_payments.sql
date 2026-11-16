CREATE TABLE payments (
    id          UUID          PRIMARY KEY,
    order_id    VARCHAR(64)   NOT NULL,
    customer_id VARCHAR(64)   NOT NULL,
    amount      NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    currency    CHAR(3)       NOT NULL,
    status      VARCHAR(10)   NOT NULL,
    reason      VARCHAR(200),
    created_at  TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_payments_order ON payments (order_id);
