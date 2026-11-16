"""Esquema PRIVADO de inventario.

Nadie fuera de este servicio debería conocer estas tablas ni sus columnas:
el contrato público es la API HTTP. Por eso podemos renombrar una columna
(versión 2) sin romper a nadie... salvo a quien se salte la API y lea la
tabla directamente (la versión ingenua del lab).
"""

from sqlalchemy import (Column, DateTime, Engine, Integer, MetaData, String, Table, func,
                        inspect, text)

SEED = {"INV-001": 12, "INV-002": 0, "INV-003": 5, "INV-004": 30}


def stock_column_name(v2: bool) -> str:
    return "units_available" if v2 else "available"


def build_tables(v2: bool) -> tuple[MetaData, Table, Table]:
    metadata = MetaData()
    stock = Table(
        "stock", metadata,
        Column("sku", String(20), primary_key=True),
        # El nombre de la columna es un detalle interno; en Python siempre se llama "available".
        Column(stock_column_name(v2), Integer, nullable=False, key="available"),
    )
    reservations = Table(
        "reservations", metadata,
        Column("id", Integer, primary_key=True, autoincrement=True),
        Column("order_id", String(64), nullable=False),
        Column("sku", String(20), nullable=False),
        Column("quantity", Integer, nullable=False),
        Column("created_at", DateTime(timezone=True), server_default=func.now()),
    )
    return metadata, stock, reservations


def init_schema(engine: Engine, v2: bool) -> tuple[Table, Table]:
    """Crea las tablas, aplica la "migración" de la v2 si corresponde y carga datos semilla."""
    metadata, stock, reservations = build_tables(v2)
    with engine.begin() as conn:
        existing = inspect(conn)
        if v2 and existing.has_table("stock"):
            columns = {c["name"] for c in existing.get_columns("stock")}
            if "available" in columns:
                conn.execute(text("ALTER TABLE stock RENAME COLUMN available TO units_available"))
        metadata.create_all(conn)
        column = stock_column_name(v2)
        for sku, qty in SEED.items():
            conn.execute(
                text(f"INSERT INTO stock (sku, {column}) VALUES (:sku, :qty) ON CONFLICT (sku) DO NOTHING"),
                {"sku": sku, "qty": qty},
            )
    return stock, reservations
