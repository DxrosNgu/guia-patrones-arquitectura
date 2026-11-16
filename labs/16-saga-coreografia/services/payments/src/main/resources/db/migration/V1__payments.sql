CREATE TABLE payments (
    id          UUID          PRIMARY KEY,
    order_id    VARCHAR(64)   NOT NULL UNIQUE,
    customer_id VARCHAR(64)   NOT NULL,
    amount      NUMERIC(12,2) NOT NULL,
    currency    CHAR(3)       NOT NULL,
    status      VARCHAR(10)   NOT NULL,      -- APPROVED | DECLINED | REFUNDED
    reason      VARCHAR(200),
    created_at  TIMESTAMPTZ   NOT NULL
);

-- Pedidos cancelados que ya conocemos (por si su StockReserved llega después).
CREATE TABLE cancellations (order_id VARCHAR(64) PRIMARY KEY);

-- Transactional Outbox (lab 15)
CREATE TABLE outbox (
    id           VARCHAR(64)  PRIMARY KEY,
    topic        VARCHAR(100) NOT NULL,
    key          VARCHAR(64)  NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT clock_timestamp(),
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pending ON outbox (created_at) WHERE published_at IS NULL;

-- Idempotent Consumer (lab 12)
CREATE TABLE processed_messages (
    event_id     VARCHAR(64) PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
