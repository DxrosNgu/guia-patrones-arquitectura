-- Una fila por Idempotency-Key recibida.
-- La PRIMARY KEY es el corazón del patrón: si dos peticiones con la misma clave
-- llegan a la vez, Postgres hace esperar a la segunda hasta que la primera termine.
CREATE TABLE idempotency_keys (
    key             VARCHAR(100) PRIMARY KEY,
    request_hash    CHAR(64)     NOT NULL,   -- SHA-256 del cuerpo: detecta "misma clave, otra petición"
    response_status INT,
    response_body   TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
