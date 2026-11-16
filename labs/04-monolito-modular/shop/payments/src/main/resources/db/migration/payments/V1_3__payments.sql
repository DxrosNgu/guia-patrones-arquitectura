-- Módulo Pagos: su propio esquema.
CREATE TABLE payments.payments (
    id          UUID PRIMARY KEY,
    order_id    UUID           NOT NULL,
    customer_id VARCHAR(100)   NOT NULL,
    amount      NUMERIC(12, 2) NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL
);
