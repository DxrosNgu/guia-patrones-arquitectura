CREATE TABLE orders (
    id               UUID          PRIMARY KEY,
    customer_id      VARCHAR(64)   NOT NULL,
    status           VARCHAR(10)   NOT NULL,
    total            NUMERIC(12,2) NOT NULL,
    currency         CHAR(3)       NOT NULL,
    rejection_reason VARCHAR(300),
    created_at       TIMESTAMPTZ   NOT NULL
);

CREATE SEQUENCE order_lines_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE order_lines (
    id         BIGINT        PRIMARY KEY,
    order_id   UUID          NOT NULL REFERENCES orders (id),
    sku        VARCHAR(20)   NOT NULL,
    quantity   INT           NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12,2) NOT NULL
);
