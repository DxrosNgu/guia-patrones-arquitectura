"""Envíos · lab 16 (saga por coreografía)."""

import asyncio
import contextlib
import logging

from aiokafka import AIOKafkaProducer
from fastapi import FastAPI
from pydantic import BaseModel, Field
from pydantic_settings import BaseSettings
from sqlalchemy import create_engine, select

from app.carrier import Carrier, Mode
from app.db import init_schema
from app.saga import ShippingParticipant
from saga_kit.consumer import consume_forever
from saga_kit.outbox import relay_forever


class Settings(BaseSettings):
    database_url: str = Field(min_length=1)
    kafka_bootstrap_servers: str | None = None
    carrier_mode: Mode = "ok"
    log_level: str = "INFO"


class CarrierMode(BaseModel):
    mode: Mode


LISTENS_TO = ["payments.events", "orders.events"]


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or Settings()
    logging.basicConfig(level=settings.log_level.upper(), format="%(levelname)s %(name)s: %(message)s")
    engine = create_engine(settings.database_url, pool_pre_ping=True)
    tables = init_schema(engine)
    carrier = Carrier(settings.carrier_mode)
    participant = ShippingParticipant(engine, tables, carrier)

    @contextlib.asynccontextmanager
    async def lifespan(_app: FastAPI):
        tasks, producer = [], None
        if settings.kafka_bootstrap_servers:
            producer = AIOKafkaProducer(bootstrap_servers=settings.kafka_bootstrap_servers)
            await producer.start()
            tasks = [asyncio.create_task(relay_forever(engine, tables.outbox, producer)),
                     asyncio.create_task(consume_forever(settings.kafka_bootstrap_servers, "shipping", LISTENS_TO,
                                                         participant.handle, producer))]
        yield
        for task in tasks:
            task.cancel()
        with contextlib.suppress(asyncio.CancelledError):
            await asyncio.gather(*tasks)
        if producer:
            await producer.stop()

    app = FastAPI(title="Envíos (saga)", lifespan=lifespan)

    @app.get("/api/carrier")
    def get_carrier():
        return {"mode": carrier.mode}

    @app.put("/api/carrier")
    def set_carrier(body: CarrierMode):
        """Control de caos: ok | down (falla transitoria) | reject (falla definitiva)."""
        carrier.mode = body.mode
        logging.getLogger("shipping").warning("🎛️  Transportista en modo '%s'", body.mode)
        return {"mode": carrier.mode}

    @app.get("/api/shipments")
    def list_shipments():
        with engine.connect() as conn:
            rows = conn.execute(select(tables.shipments).order_by(tables.shipments.c.created_at.desc()).limit(100))
            return [{"orderId": r.order_id, "status": r.status, "trackingCode": r.tracking_code} for r in rows]

    @app.get("/health")
    def health():
        return {"status": "UP"}

    return app
