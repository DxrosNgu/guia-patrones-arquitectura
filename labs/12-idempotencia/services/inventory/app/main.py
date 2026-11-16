"""Inventario · lab 12 (Idempotent Consumer)."""

import asyncio
import contextlib
import logging
import socket

from fastapi import FastAPI
from sqlalchemy import create_engine, select

from app.config import Settings
from app.consumer import run_consumer
from app.db import init_schema
from app.events import OrderEventHandler

log = logging.getLogger("inventory")


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or Settings()
    logging.basicConfig(level=settings.log_level.upper(), format="%(levelname)s %(name)s: %(message)s")
    engine = create_engine(settings.database_url, pool_pre_ping=True)
    tables = init_schema(engine)
    stock, reservations, processed = tables

    @contextlib.asynccontextmanager
    async def lifespan(_app: FastAPI):
        task = None
        if settings.kafka_bootstrap_servers:
            handler = OrderEventHandler(engine, tables, socket.gethostname(), settings.idempotent_consumer)
            log.info("Consumidor idempotente: %s", "SÍ" if settings.idempotent_consumer else "NO ❌")
            task = asyncio.create_task(run_consumer(settings, handler))
        yield
        if task:
            task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await task

    app = FastAPI(title="Inventario", lifespan=lifespan)

    @app.get("/api/stock")
    def list_stock():
        with engine.connect() as conn:
            rows = conn.execute(select(stock.c.sku, stock.c.available).order_by(stock.c.sku))
            return [{"sku": sku, "available": qty} for sku, qty in rows]

    @app.get("/api/reservations")
    def list_reservations():
        with engine.connect() as conn:
            rows = conn.execute(select(reservations).order_by(reservations.c.id.desc()).limit(200))
            return [{"orderId": r.order_id, "sku": r.sku, "quantity": r.quantity, "status": r.status,
                     "reason": r.reason, "processedBy": r.processed_by} for r in rows]

    @app.get("/api/processed-messages")
    def list_processed():
        with engine.connect() as conn:
            rows = conn.execute(select(processed).order_by(processed.c.processed_at.desc()).limit(200))
            return [{"eventId": r.event_id, "orderId": r.order_id, "outcome": r.outcome,
                     "processedBy": r.processed_by} for r in rows]

    @app.get("/health")
    def health():
        return {"status": "UP"}

    return app
