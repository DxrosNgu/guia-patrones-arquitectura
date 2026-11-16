"""Inventario · lab 05. Dueño exclusivo de su base de datos `inventory`."""

import logging

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy import create_engine, select, update

from app.config import Settings
from app.db import init_schema

log = logging.getLogger("inventory")


class Item(BaseModel):
    sku: str
    quantity: int = Field(gt=0)


class ReservationRequest(BaseModel):
    orderId: str
    items: list[Item] = Field(min_length=1)


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or Settings()
    logging.basicConfig(level=settings.log_level.upper())
    engine = create_engine(settings.database_url, pool_pre_ping=True)
    stock, reservations = init_schema(engine, settings.inventory_v2)

    app = FastAPI(title="Inventario", version="2.0.0" if settings.inventory_v2 else "1.0.0")
    app.state.settings = settings

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
    def reserve(request: ReservationRequest):
        # Una transacción LOCAL: todo o nada, pero solo dentro de ESTA base.
        with engine.begin() as conn:
            for item in request.items:
                current = conn.execute(
                    select(stock.c.available).where(stock.c.sku == item.sku).with_for_update()
                ).scalar()
                if current is None:
                    raise HTTPException(404, f"No existe el SKU {item.sku}")
                if current < item.quantity:
                    raise HTTPException(409, f"Stock insuficiente para {item.sku}: hay {current}")
                conn.execute(
                    update(stock).where(stock.c.sku == item.sku)
                    .values(available=stock.c.available - item.quantity)
                )
                conn.execute(reservations.insert().values(
                    order_id=request.orderId, sku=item.sku, quantity=item.quantity))
        log.info("Reserva creada para el pedido %s", request.orderId)
        return {"orderId": request.orderId, "status": "RESERVED"}

    @app.get("/health")
    def health():
        return {"status": "UP"}

    return app
