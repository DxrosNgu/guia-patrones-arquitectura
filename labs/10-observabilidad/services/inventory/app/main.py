"""Inventario · lab 10 (observabilidad)."""

import asyncio
import contextlib
import logging
import socket
import time

from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from opentelemetry.instrumentation.sqlalchemy import SQLAlchemyInstrumentor
from sqlalchemy import create_engine, select

from app.config import Settings
from app.consumer import ConsumerState, run_consumer
from app.db import init_schema
from app.events import OrderEventHandler
from app.health import readiness
from app.telemetry import setup_telemetry

log = logging.getLogger("inventory")


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or Settings()
    logging.basicConfig(level=settings.log_level.upper(), format="%(levelname)s %(name)s: %(message)s")
    telemetry_on = setup_telemetry(settings)

    engine = create_engine(settings.database_url, pool_pre_ping=True)
    if telemetry_on:
        SQLAlchemyInstrumentor().instrument(engine=engine)   # un span por consulta SQL
    tables = init_schema(engine)
    stock, reservations = tables
    consumer_state = ConsumerState()

    @contextlib.asynccontextmanager
    async def lifespan(_app: FastAPI):
        task = None
        if settings.kafka_bootstrap_servers:
            handler = OrderEventHandler(engine, tables, socket.gethostname())
            task = asyncio.create_task(run_consumer(settings, handler, consumer_state))
        yield
        if task:
            task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await task

    app = FastAPI(title="Inventario", lifespan=lifespan)
    if telemetry_on:
        FastAPIInstrumentor.instrument_app(app)               # un span por petición HTTP

    @app.get("/api/stock/{sku}")
    def stock_of(sku: str):
        if settings.stock_lookup_delay_ms:
            time.sleep(settings.stock_lookup_delay_ms / 1000)  # 🐢 experimento: ¿lo encuentras en la traza?
        with engine.connect() as conn:
            qty = conn.execute(select(stock.c.available).where(stock.c.sku == sku)).scalar()
        if qty is None:
            raise HTTPException(404, f"No existe el SKU {sku}")
        return {"sku": sku, "available": qty}

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

    @app.get("/health/live")
    def live():
        return {"status": "UP"}

    @app.get("/health/ready")
    def ready():
        consumer_alive = consumer_state.running if settings.kafka_bootstrap_servers else None
        status, checks = readiness(engine, consumer_alive)
        return JSONResponse({"status": status, "checks": checks}, status_code=200 if status == "UP" else 503)

    return app
