from dataclasses import dataclass

from sqlalchemy import Column, DateTime, Engine, MetaData, String, Table, func

from saga_kit.tables import kit_tables


@dataclass
class Tables:
    shipments: Table
    outbox: Table
    processed: Table


def init_schema(engine: Engine) -> Tables:
    metadata = MetaData()
    shipments = Table("shipments", metadata,
                      Column("order_id", String(64), primary_key=True),
                      Column("status", String(10), nullable=False),      # SCHEDULED
                      Column("tracking_code", String(20)),
                      Column("created_at", DateTime(timezone=True), server_default=func.now()))
    outbox, processed = kit_tables(metadata)
    metadata.create_all(engine)
    return Tables(shipments, outbox, processed)
