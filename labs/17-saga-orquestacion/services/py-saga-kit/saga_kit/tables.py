from sqlalchemy import Column, DateTime, MetaData, String, Table, Text, func


def kit_tables(metadata: MetaData) -> tuple[Table, Table]:
    outbox = Table(
        "outbox", metadata,
        Column("id", String(64), primary_key=True),          # = eventId
        Column("topic", String(100), nullable=False),
        Column("key", String(64), nullable=False),           # = id del pedido (orden por pedido)
        Column("payload", Text, nullable=False),
        Column("created_at", DateTime(timezone=True), server_default=func.now(), nullable=False),
        Column("published_at", DateTime(timezone=True)),
    )
    processed = Table(
        "processed_messages", metadata,
        Column("event_id", String(64), primary_key=True),
        Column("processed_at", DateTime(timezone=True), server_default=func.now()),
    )
    return outbox, processed
