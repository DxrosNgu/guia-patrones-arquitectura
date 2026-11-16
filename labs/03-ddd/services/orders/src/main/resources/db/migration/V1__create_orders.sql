-- Versión DDD: el agregado Pedido. Las líneas viven en otra tabla, pero se cargan y guardan SIEMPRE con su pedido.
CREATE TABLE orders (
    id          UUID PRIMARY KEY,
    customer_id VARCHAR(100) NOT NULL,
    currency    VARCHAR(3)   NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL
    -- Sin columna "total": se calcula a partir de las líneas.
);

CREATE TABLE order_lines (
    order_id   UUID           NOT NULL REFERENCES orders (id),
    position   INT            NOT NULL,
    sku        VARCHAR(50)    NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    currency   VARCHAR(3)     NOT NULL,
    quantity   INT            NOT NULL CHECK (quantity BETWEEN 1 AND 10),
    PRIMARY KEY (order_id, position)
);

-- Versión ingenua (modelo anémico).
CREATE TABLE anemic_orders (
    id          UUID PRIMARY KEY,
    customer_id VARCHAR(100),
    status      VARCHAR(50),
    total       DOUBLE PRECISION
);

CREATE TABLE anemic_order_lines (
    order_id   UUID NOT NULL REFERENCES anemic_orders (id),
    sku        VARCHAR(50),
    unit_price DOUBLE PRECISION,
    quantity   INT
);
