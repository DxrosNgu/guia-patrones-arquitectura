from dataclasses import dataclass

from sqlalchemy import Column, DateTime, Engine, Integer, MetaData, String, Table, func, text

from saga_kit.tables import kit_tables

# INV-005 tiene UNA unidad: sirve para el caso S12 (dos sagas compiten por la última).
SEED = {"INV-001": 12, "INV-002": 0, "INV-003": 5, "INV-004": 30, "INV-005": 1}


@dataclass
class Tables:
    stock: Table
    reservations: Table
    outbox: Table
    processed: Table


def init_schema(engine: Engine) -> Tables:
    metadata = MetaData()
    stock = Table("stock", metadata,
                  Column("sku", String(20), primary_key=True),
                  Column("available", Integer, nullable=False))
    reservations = Table("reservations", metadata,
                         Column("id", Integer, primary_key=True, autoincrement=True),
                         Column("order_id", String(64), nullable=False, index=True),
                         Column("sku", String(20), nullable=False),
                         Column("quantity", Integer, nullable=False),
                         Column("status", String(10), nullable=False),     # RESERVED | RELEASED
                         Column("created_at", DateTime(timezone=True), server_default=func.now()))
    outbox, processed = kit_tables(metadata)
    with engine.begin() as conn:
        metadata.create_all(conn)
        for sku, qty in SEED.items():
            conn.execute(text("INSERT INTO stock VALUES (:s, :q) ON CONFLICT (sku) DO NOTHING"), {"s": sku, "q": qty})
    return Tables(stock, reservations, outbox, processed)
