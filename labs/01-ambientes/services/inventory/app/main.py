"""Inventario · servicio FastAPI del lab 01.

Se arranca con una *factory* (`uvicorn app.main:create_app --factory`) para que
la configuración se lea al iniciar el proceso y los tests puedan inyectar la suya.
"""

import logging

from fastapi import FastAPI, HTTPException

from app.config import Settings
from app.logging_setup import configure_logging

# Stock en memoria: suficiente para este lab, cuyo foco es la configuración.
SEED_STOCK = {"INV-001": 12, "INV-002": 0, "INV-003": 5, "INV-004": 30}

log = logging.getLogger("inventory")


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or Settings()
    configure_logging(settings.log_level, settings.log_json)
    stock = dict(SEED_STOCK)

    app = FastAPI(title="Inventario", version="1.0.0")

    @app.get("/api/info")
    def info():
        return {
            "service": "inventory",
            "environment": settings.app_env,
            "logLevel": settings.log_level.upper(),
            "logJson": settings.log_json,
            "supplierKeyHint": settings.supplier_key_hint(),
        }

    @app.get("/api/stock")
    def list_stock():
        log.debug("Listando stock de %d inventos", len(stock))
        return [{"sku": sku, "available": qty} for sku, qty in sorted(stock.items())]

    @app.get("/api/stock/{sku}")
    def stock_of(sku: str):
        if sku not in stock:
            raise HTTPException(status_code=404, detail=f"No existe el SKU {sku}")
        return {"sku": sku, "available": stock[sku]}

    @app.get("/health")
    def health():
        return {"status": "UP"}

    log.info("Inventario iniciado en el ambiente '%s'", settings.app_env)
    return app
