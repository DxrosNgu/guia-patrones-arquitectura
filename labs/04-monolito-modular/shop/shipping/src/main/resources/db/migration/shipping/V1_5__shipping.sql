-- Módulo Envíos: su propio esquema.
CREATE TABLE shipping.shipments (
    order_id   UUID PRIMARY KEY,
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
