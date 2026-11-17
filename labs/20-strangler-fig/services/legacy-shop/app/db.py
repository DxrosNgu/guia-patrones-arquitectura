"""La base del monolito: catálogo y pedidos juntos, con nombres de 2012 (productos, pedidos, cliente)."""
from sqlalchemy import Engine, text

PRODUCTS = [
    ("INV-001", "Paraguas solar", "49.90"),
    ("INV-002", "Lámpara de agua salada", "89.00"),
    ("INV-003", "Mochila con ventilador", "120.00"),
    ("INV-004", "Taza que avisa cuando se enfría", "25.50"),
]

SCHEMA = """
CREATE TABLE IF NOT EXISTS productos (
    sku    VARCHAR(20) PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    precio NUMERIC(10, 2) NOT NULL
);
CREATE SEQUENCE IF NOT EXISTS pedidos_id_seq START 1001;
CREATE TABLE IF NOT EXISTS pedidos (
    id       BIGINT PRIMARY KEY DEFAULT nextval('pedidos_id_seq'),
    cliente  VARCHAR(50) NOT NULL,
    sku      VARCHAR(20) NOT NULL,
    cantidad INT NOT NULL,
    total    NUMERIC(10, 2) NOT NULL,
    creado   TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Agregada PARA LA MIGRACIÓN: de dónde viene el pedido ('legado' o 'nuevo', si lo sincronizó el servicio nuevo).
    origen   VARCHAR(10) NOT NULL DEFAULT 'legado'
);
"""


def init_schema(engine: Engine) -> None:
    with engine.begin() as conn:
        for statement in SCHEMA.split(";"):
            if statement.strip():
                conn.execute(text(statement))
        for sku, name, price in PRODUCTS:
            conn.execute(text("INSERT INTO productos VALUES (:s, :n, :p) ON CONFLICT (sku) DO NOTHING"),
                         {"s": sku, "n": name, "p": price})
        if conn.execute(text("SELECT count(*) FROM pedidos")).scalar() == 0:
            # Pedidos de antes de la migración: tienen que seguir visibles cuando /orders pase al servicio nuevo.
            for customer, sku, qty in [("ana", "INV-001", 2), ("ana", "INV-004", 1), ("beto", "INV-003", 1)]:
                conn.execute(text("INSERT INTO pedidos (cliente, sku, cantidad, total) "
                                  "SELECT :c, sku, :q, precio * :q FROM productos WHERE sku = :s"),
                             {"c": customer, "s": sku, "q": qty})
