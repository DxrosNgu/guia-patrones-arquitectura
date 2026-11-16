-- Módulo Pedidos: su propio esquema. Guarda SKU y cantidad, no una FK a inventory.stock:
-- entre módulos se referencia por id, nunca con claves foráneas cruzadas.
CREATE TABLE orders.orders (
    id          UUID PRIMARY KEY,
    customer_id VARCHAR(100)   NOT NULL,
    total       NUMERIC(12, 2) NOT NULL,
    status      VARCHAR(20)    NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL
);

CREATE TABLE orders.order_lines (
    order_id UUID        NOT NULL REFERENCES orders.orders (id),
    sku      VARCHAR(20) NOT NULL,
    quantity INT         NOT NULL,
    PRIMARY KEY (order_id, sku)
);
