"""Inventario · lab 16 (saga por coreografía)."""

import asyncio
import contextlib
import logging

from aiokafka import AIOKafkaProducer
from fastapi import FastAPI
from pydantic import Field
from pydantic_settings import BaseSettings
from sqlalchemy import create_engine, select

from app.db import init_schema
from app.saga import InventoryParticipant
from saga_kit.consumer import consume_forever
from saga_kit.outbox import relay_forever


class Settings(BaseSettings):
    database_url: str = Field(min_length=1)
    kafka_bootstrap_servers: str | None = None
    log_level: str = "INFO"


# Coreografía: inventario escucha los eventos de TODOS los que le interesan.
LISTENS_TO = ["orders.events", "payments.events", "shipping.events"]


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or Settings()
    logging.basicConfig(level=settings.log_level.upper(), format="%(levelname)s %(name)s: %(message)s")
    engine = create_engine(settings.database_url, pool_pre_ping=True)
    tables = init_schema(engine)
    participant = InventoryParticipant(engine, tables)

    @contextlib.asynccontextmanager
    async def lifespan(_app: FastAPI):
        tasks, producer = [], None
        if settings.kafka_bootstrap_servers:
            producer = AIOKafkaProducer(bootstrap_servers=settings.kafka_bootstrap_servers)
            await producer.start()
            tasks = [asyncio.create_task(relay_forever(engine, tables.outbox, producer)),
                     asyncio.create_task(consume_forever(settings.kafka_bootstrap_servers, "inventory", LISTENS_TO,
                                                         participant.handle, producer))]
        yield
        for task in tasks:
            task.cancel()
        with contextlib.suppress(asyncio.CancelledError):
            await asyncio.gather(*tasks)
        if producer:
            await producer.stop()

    app = FastAPI(title="Inventario (saga)", lifespan=lifespan)

    @app.get("/api/stock")
    def list_stock():
        with engine.connect() as conn:
            rows = conn.execute(select(tables.stock).order_by(tables.stock.c.sku))
            return [{"sku": r.sku, "available": r.available} for r in rows]

    @app.get("/api/reservations")
    def list_reservations():
        with engine.connect() as conn:
            rows = conn.execute(select(tables.reservations).order_by(tables.reservations.c.id.desc()).limit(100))
            return [{"orderId": r.order_id, "sku": r.sku, "quantity": r.quantity, "status": r.status} for r in rows]

    @app.get("/health")
    def health():
        return {"status": "UP"}

    return app
