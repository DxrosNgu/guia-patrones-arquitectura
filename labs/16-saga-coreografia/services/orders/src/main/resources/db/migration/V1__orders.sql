CREATE TABLE orders (
    id          UUID          PRIMARY KEY,
    customer_id VARCHAR(64)   NOT NULL,
    status      VARCHAR(20)   NOT NULL,
    total       NUMERIC(12,2) NOT NULL,
    currency    CHAR(3)       NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL
);

CREATE TABLE order_items (
    order_id   UUID          NOT NULL REFERENCES orders (id),
    sku        VARCHAR(20)   NOT NULL,
    quantity   INT           NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL
);

CREATE SEQUENCE order_timeline_seq START WITH 1 INCREMENT BY 50;
CREATE TABLE order_timeline (
    id          BIGINT       PRIMARY KEY,
    order_id    UUID         NOT NULL,
    at          TIMESTAMPTZ  NOT NULL,
    event_type  VARCHAR(40)  NOT NULL,
    description VARCHAR(300) NOT NULL,
    applied     BOOLEAN      NOT NULL
);
CREATE INDEX idx_timeline_order ON order_timeline (order_id, id);

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
