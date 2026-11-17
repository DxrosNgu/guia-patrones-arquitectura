CREATE TABLE inventions (
    sku   VARCHAR(20) PRIMARY KEY,
    name  VARCHAR(200)   NOT NULL,
    price NUMERIC(12, 2) NOT NULL
);

INSERT INTO inventions (sku, name, price) VALUES
    ('INV-001', 'Paraguas solar', 49.90),
    ('INV-002', 'Lámpara de agua salada', 89.00),
    ('INV-003', 'Mochila con ventilador', 120.00),
    ('INV-004', 'Taza que avisa cuando se enfría', 25.50);
