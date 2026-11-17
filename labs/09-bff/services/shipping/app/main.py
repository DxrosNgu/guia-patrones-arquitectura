"""Envíos: estado del despacho de VARIOS pedidos en una llamada (?orderIds=a,b,c)."""
import asyncio
import os

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

SHIPMENTS = [
    {"orderId": "ord-1001", "status": "DELIVERED", "carrier": "Rápido SAC", "trackingCode": "RS-88231",
     "eta": "2026-11-05"},
    {"orderId": "ord-1002", "status": "IN_TRANSIT", "carrier": "Rápido SAC", "trackingCode": "RS-90412",
     "eta": "2026-11-19"},
]


class Delay(BaseModel):
    ms: int


def create_app() -> FastAPI:
    app = FastAPI(title="Envíos")
    state = {"delay_ms": int(os.getenv("DELAY_MS", "0"))}

    @app.get("/api/shipments")
    async def by_orders(orderIds: str = ""):  # noqa: N803 (nombre del query param)
        ids = {i.strip() for i in orderIds.split(",") if i.strip()}
        if not ids:
            raise HTTPException(400, "Falta orderIds")
        await asyncio.sleep(state["delay_ms"] / 1000)
        return [s for s in SHIPMENTS if s["orderId"] in ids]

    @app.put("/admin/delay")
    def set_delay(delay: Delay):
        """Caos: latencia inyectada en cada respuesta."""
        state["delay_ms"] = max(0, delay.ms)
        return {"ms": state["delay_ms"]}

    @app.get("/health")
    def health():
        return {"status": "UP"}

    return app
