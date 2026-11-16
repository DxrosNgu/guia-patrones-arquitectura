-- Módulo Inventario: su propio esquema.
CREATE TABLE inventory.stock (
    sku       VARCHAR(20) PRIMARY KEY,
    available INT NOT NULL CHECK (available >= 0)
);

CREATE TABLE inventory.reservations (
    id       BIGSERIAL PRIMARY KEY,
    order_id UUID        NOT NULL,
    sku      VARCHAR(20) NOT NULL,
    quantity INT         NOT NULL
);

INSERT INTO inventory.stock (sku, available) VALUES ('INV-001', 12), ('INV-002', 0), ('INV-003', 5), ('INV-004', 30);
