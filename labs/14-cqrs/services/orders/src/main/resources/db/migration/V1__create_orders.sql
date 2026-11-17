CREATE TABLE orders (
    id          UUID PRIMARY KEY,
    customer_id VARCHAR(64)    NOT NULL,
    status      VARCHAR(20)    NOT NULL,
    total       NUMERIC(12, 2) NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL,
    version     INT            NOT NULL
);

CREATE TABLE order_items (
    order_id   UUID           NOT NULL REFERENCES orders (id),
    sku        VARCHAR(20)    NOT NULL,
    name       VARCHAR(200)   NOT NULL,
    quantity   INT            NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12, 2) NOT NULL
);
