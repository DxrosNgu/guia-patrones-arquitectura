CREATE TABLE orders (
    id          UUID        PRIMARY KEY,
    customer_id VARCHAR(64) NOT NULL,
    status      VARCHAR(10) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE TABLE order_items (
    order_id UUID        NOT NULL REFERENCES orders (id),
    sku      VARCHAR(20) NOT NULL,
    quantity INT         NOT NULL CHECK (quantity > 0)
);
