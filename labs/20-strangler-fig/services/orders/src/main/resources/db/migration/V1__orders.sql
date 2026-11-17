-- Pedidos NUEVO. Mientras dura la migración conviven pedidos creados aquí y en el legado.

-- Un RANGO DE IDS propio: el legado usa 1001, 1002… y este servicio 500001, 500002…
-- Así un pedido tiene el MISMO id en las dos bases y nunca chocan.
CREATE SEQUENCE order_ids START 500001;

CREATE TABLE orders (
    id               BIGINT PRIMARY KEY,
    customer         VARCHAR(50)    NOT NULL,
    sku              VARCHAR(20)    NOT NULL,
    quantity         INT            NOT NULL,
    total            NUMERIC(10, 2) NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL,
    origin           VARCHAR(10)    NOT NULL,              -- NEW (creado aquí) o LEGACY (copiado del legado)
    -- Se marca en la MISMA transacción que el pedido: si el legado está caído, el pedido queda pendiente
    -- y se reintenta. Es una outbox (lab 15) reducida a una columna.
    synced_to_legacy BOOLEAN        NOT NULL DEFAULT false
);
CREATE INDEX orders_by_customer ON orders (customer, id DESC);
CREATE INDEX orders_pending_sync ON orders (id) WHERE NOT synced_to_legacy;

-- Hasta qué id del legado ya se copió.
CREATE TABLE sync_cursor (
    name    VARCHAR(50) PRIMARY KEY,
    last_id BIGINT      NOT NULL
);
INSERT INTO sync_cursor VALUES ('legacy-orders', 0);
