"""Raíz de composición: el ÚNICO lugar que conoce a la vez los puertos y los adaptadores."""
import logging

from fastapi import FastAPI
from sqlalchemy import create_engine

from app.adapters.memory_repository import InMemoryInventionRepository
from app.adapters.rest import build_router, register_error_handlers
from app.adapters.sqlalchemy_repository import SqlAlchemyInventionRepository, init_schema
from app.application.catalog_service import CatalogService
from app.application.ports import InventionRepository
from app.config import Settings


def build_repository(settings: Settings) -> InventionRepository:
    if settings.persistence == "memory":
        return InMemoryInventionRepository()
    if not settings.database_url:
        raise ValueError("Con CATALOG_PERSISTENCE=postgres hace falta DATABASE_URL")
    engine = create_engine(settings.database_url, pool_pre_ping=True)
    init_schema(engine)
    return SqlAlchemyInventionRepository(engine)


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or Settings()
    logging.basicConfig(level=settings.log_level)
    repository = build_repository(settings)
    catalog = CatalogService(repository)

    app = FastAPI(title="Catálogo · hexagonal (Python)")
    app.include_router(build_router(catalog))
    register_error_handlers(app)

    @app.get("/api/info")
    def info():
        return {"architecture": "hexagonal-py", "persistence": repository.name}

    @app.get("/health")
    def health():
        return {"status": "UP"}

    return app
