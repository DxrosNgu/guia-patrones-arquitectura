"""Reporte de ventas · lab 05.

Lo usa "otro equipo" (Business Intelligence) que necesita datos de pedidos y
de inventario a la vez. Según REPORT_SOURCE, los obtiene por API o leyendo
directamente la base compartida.
"""

from fastapi import FastAPI, HTTPException
from sqlalchemy import create_engine

from app.config import Settings
from app.sources import ApiSource, DatabaseSource, SourceError


def build_source(settings: Settings):
    if settings.report_source == "database":
        return DatabaseSource(create_engine(settings.database_url, pool_pre_ping=True))
    return ApiSource(settings.orders_url, settings.inventory_url)


def create_app(settings: Settings | None = None, source=None) -> FastAPI:
    settings = settings or Settings()
    source = source or build_source(settings)

    app = FastAPI(title="Reporte de ventas")

    @app.get("/api/report")
    def report():
        try:
            return {"source": source.name, "orders": source.fetch()}
        except SourceError as error:
            raise HTTPException(502, f"El reporte falló: {error}") from error

    @app.get("/health")
    def health():
        return {"status": "UP"}

    return app
