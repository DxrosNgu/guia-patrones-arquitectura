CREATE TABLE inventions (
    id       BIGINT        PRIMARY KEY,
    sku      VARCHAR(20)   NOT NULL UNIQUE,
    name     VARCHAR(120)  NOT NULL,
    inventor VARCHAR(120)  NOT NULL,
    price    NUMERIC(12,2) NOT NULL CHECK (price > 0),
    currency CHAR(3)       NOT NULL,
    featured BOOLEAN       NOT NULL DEFAULT FALSE
);

-- Datos de ejemplo. Flyway ejecuta cada migración una sola vez,
-- así que levantar el lab varias veces no duplica filas.
INSERT INTO inventions (id, sku, name, inventor, price, currency, featured) VALUES
    (1, 'INV-001', 'Paraguas solar',             'Ada Quispe',     49.90, 'PEN', TRUE),
    (2, 'INV-002', 'Taza que avisa si se enfría', 'Luis Huamán',    35.00, 'PEN', FALSE),
    (3, 'INV-003', 'Mochila con GPS',            'Carla Mendoza', 189.00, 'PEN', TRUE),
    (4, 'INV-004', 'Lámpara de lectura plegable', 'Jorge Salazar',  72.50, 'PEN', FALSE);
