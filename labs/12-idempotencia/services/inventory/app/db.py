from sqlalchemy import Column, DateTime, Engine, Integer, MetaData, String, Table, func, text

SEED = {"INV-001": 12, "INV-002": 0, "INV-003": 5, "INV-004": 30}


def init_schema(engine: Engine) -> tuple[Table, Table, Table]:
    metadata = MetaData()
    stock = Table(
        "stock", metadata,
        Column("sku", String(20), primary_key=True),
        Column("available", Integer, nullable=False),
    )
    reservations = Table(
        "reservations", metadata,
        Column("id", Integer, primary_key=True, autoincrement=True),
        Column("order_id", String(64), nullable=False),
        Column("sku", String(20), nullable=False),
        Column("quantity", Integer, nullable=False),
        Column("status", String(10), nullable=False),
        Column("reason", String(200)),
        Column("processed_by", String(100), nullable=False),
        Column("created_at", DateTime(timezone=True), server_default=func.now()),
    )
    # 🔑 Idempotent Consumer: la PRIMARY KEY sobre event_id es lo que impide procesar dos veces.
    processed_messages = Table(
        "processed_messages", metadata,
        Column("event_id", String(64), primary_key=True),
        Column("order_id", String(64), nullable=False),
        Column("outcome", String(10)),
        Column("processed_by", String(100), nullable=False),
        Column("processed_at", DateTime(timezone=True), server_default=func.now()),
    )
    with engine.begin() as conn:
        metadata.create_all(conn)
        for sku, qty in SEED.items():
            conn.execute(text("INSERT INTO stock (sku, available) VALUES (:s, :q) ON CONFLICT (sku) DO NOTHING"),
                         {"s": sku, "q": qty})
    return stock, reservations, processed_messages
