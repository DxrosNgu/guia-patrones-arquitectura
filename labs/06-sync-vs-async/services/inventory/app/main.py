"""Inventario · lab 06. Recibe pedidos por dos caminos:

  - REST  POST /api/reservations  → modo síncrono (pedidos espera la respuesta)
  - Kafka topic orders.events      → modo asíncrono (procesa cuando puede)
"""

import asyncio
import contextlib
import logging
import socket

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy import create_engine, select

from app.config import Settings
from app.consumer import orders_items_fetcher, run_consumer
from app.db import init_schema
from app.events import OrderEventHandler
from app.reservations import reserve

log = logging.getLogger("inventory")


class Item(BaseModel):
    sku: str
    quantity: int = Field(gt=0)


class ReservationRequest(BaseModel):
    orderId: str
    items: list[Item] = Field(min_length=1)


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or Settings()
    logging.basicConfig(level=settings.log_level.upper(), format="%(levelname)s %(name)s: %(message)s")
    engine = create_engine(settings.database_url, pool_pre_ping=True)
    tables = init_schema(engine)
    stock, reservations = tables
    me = socket.gethostname()

    @contextlib.asynccontextmanager
    async def lifespan(_app: FastAPI):
        task = None
        if settings.kafka_bootstrap_servers:
            handler = OrderEventHandler(engine, tables, orders_items_fetcher(settings.orders_url), me)
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

    @app.get("/api/stock/{sku}")
    def stock_of(sku: str):
        with engine.connect() as conn:
            qty = conn.execute(select(stock.c.available).where(stock.c.sku == sku)).scalar()
        if qty is None:
            raise HTTPException(404, f"No existe el SKU {sku}")
        return {"sku": sku, "available": qty}

    @app.post("/api/reservations", status_code=201)
    def reserve_now(request: ReservationRequest):
        result = reserve(engine, tables, request.orderId, [i.model_dump() for i in request.items], me)
        if result.status == "REJECTED":
            raise HTTPException(404 if result.unknown_sku else 409, result.reason)
        return {"orderId": request.orderId, "status": "RESERVED"}

    @app.get("/api/reservations")
    def list_reservations():
        with engine.connect() as conn:
            rows = conn.execute(select(reservations).order_by(reservations.c.id.desc()).limit(200))
            return [{"orderId": r.order_id, "sku": r.sku, "quantity": r.quantity, "status": r.status,
                     "reason": r.reason, "processedBy": r.processed_by} for r in rows]

    @app.get("/health")
    def health():
        return {"status": "UP"}

    return app
