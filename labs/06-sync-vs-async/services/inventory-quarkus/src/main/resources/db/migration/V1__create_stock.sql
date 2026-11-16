-- Inventario (versión Quarkus): su propio esquema dentro de la base "inventory".
-- Es otro grupo de consumidores: recibe TODOS los eventos, igual que la versión Python.
CREATE TABLE stock (
    sku       VARCHAR(20) PRIMARY KEY,
    available INT NOT NULL CHECK (available >= 0)
);

CREATE TABLE reservations (
    id         BIGSERIAL PRIMARY KEY,
    order_id   VARCHAR(64) NOT NULL,
    sku        VARCHAR(20) NOT NULL,
    quantity   INT         NOT NULL,
    status     VARCHAR(10) NOT NULL,
    reason     VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO stock (sku, available) VALUES ('INV-001', 12), ('INV-002', 0), ('INV-003', 5), ('INV-004', 30);
