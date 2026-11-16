"""Adaptador de salida para Postgres (SQLAlchemy Core). Traduce entre el dominio y la tabla."""
from uuid import UUID

from sqlalchemy import (Column, DateTime, Engine, MetaData, Numeric, String, Table, Uuid, insert, select,
                        update)

from app.domain.invention import Invention, InventionStatus, Price

SCHEMA = "hexagonal_py"
metadata = MetaData(schema=SCHEMA)
inventions = Table(
    "inventions", metadata,
    Column("id", Uuid, primary_key=True),
    Column("inventor_id", String(100), nullable=False, index=True),
    Column("name", String(200), nullable=False),
    Column("price", Numeric(12, 2), nullable=False),
    Column("image_url", String(500)),
    Column("status", String(20), nullable=False),
    Column("created_at", DateTime(timezone=True), nullable=False),
)


def init_schema(engine: Engine) -> None:
    with engine.begin() as conn:
        conn.exec_driver_sql(f"CREATE SCHEMA IF NOT EXISTS {SCHEMA}")
        metadata.create_all(conn)


def _to_domain(row) -> Invention:
    return Invention(id=row.id, inventor_id=row.inventor_id, name=row.name, price=Price(row.price),
                     image_url=row.image_url, status=InventionStatus(row.status), created_at=row.created_at)


class SqlAlchemyInventionRepository:
    name = "postgres"

    def __init__(self, engine: Engine):
        self._engine = engine

    def save(self, invention: Invention) -> None:
        values = {"inventor_id": invention.inventor_id, "name": invention.name, "price": invention.price.amount,
                  "image_url": invention.image_url, "status": invention.status.value,
                  "created_at": invention.created_at}
        with self._engine.begin() as conn:
            updated = conn.execute(update(inventions).where(inventions.c.id == invention.id).values(**values))
            if updated.rowcount == 0:
                conn.execute(insert(inventions).values(id=invention.id, **values))

    def find_by_id(self, invention_id: UUID) -> Invention | None:
        with self._engine.connect() as conn:
            row = conn.execute(select(inventions).where(inventions.c.id == invention_id)).first()
        return _to_domain(row) if row else None

    def find_by_inventor(self, inventor_id: str) -> list[Invention]:
        query = select(inventions).where(inventions.c.inventor_id == inventor_id).order_by(inventions.c.created_at)
        with self._engine.connect() as conn:
            return [_to_domain(row) for row in conn.execute(query)]
