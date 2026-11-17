"""La API de CONSULTA del historial: solo lee la vista. Nunca escribe, nunca llama a otro servicio."""
import time

from fastapi import FastAPI
from fastapi.responses import JSONResponse
from pymongo import MongoClient

from app.config import Settings
from app.projection import Projection


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or Settings()
    client = MongoClient(settings.mongo_url)
    projection = Projection(client[settings.mongo_db])
    app = FastAPI(title="Historial de pedidos (lado de lectura)")

    @app.get("/api/history")
    def history(customerId: str, status: str | None = None, q: str | None = None, limit: int = 50):
        return projection.search(customerId, status=status, text=q, limit=min(limit, 200))

    @app.get("/api/history/status")
    def status():
        return projection.status()

    @app.get("/api/history/{order_id}")
    def one(order_id: str, minVersion: int = 0, waitMs: int = 0):
        """Read-your-writes: el cliente sabe qué versión escribió y pide "como mínimo esa".
        Si la vista todavía no la tiene, se espera un poco (long polling) en lugar de mentir."""
        deadline = time.monotonic() + min(waitMs, 10_000) / 1000
        while True:
            doc = projection.get(order_id)
            if doc and doc.get("orderVersion", 0) >= minVersion:
                return doc
            if time.monotonic() >= deadline:
                return JSONResponse(status_code=404, headers={"Retry-After": "1"}, content={
                    "code": "NOT_YET_PROJECTED", "message": "La vista todavía no tiene esa versión del pedido"})
            time.sleep(0.1)

    @app.get("/health")
    def health():
        client.admin.command("ping")
        return {"status": "UP"}

    return app
