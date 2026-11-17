"""Inventario para el lab de discovery: responde stock y dice QUÉ instancia respondió."""
import logging
import socket
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Response
from pydantic import BaseModel

from app.config import Settings
from app.consul import ConsulRegistry, registration

STOCK = {"INV-001": 12, "INV-002": 0, "INV-003": 5, "INV-004": 30}


class HealthChange(BaseModel):
    healthy: bool


def create_app(settings: Settings | None = None, registry: ConsulRegistry | None = None) -> FastAPI:
    settings = settings or Settings()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)-5s [%(name)s] %(message)s")
    if registry is None and settings.consul_url:
        registry = ConsulRegistry(settings.consul_url)
    state = {"healthy": True}

    @asynccontextmanager
    async def lifespan(_app: FastAPI):
        if registry:
            address = settings.advertise_address or socket.gethostbyname(socket.gethostname())
            registry.register(registration(settings.service_name, settings.instance_name, address, settings.port))
        yield
        if registry:
            registry.deregister(settings.instance_name)   # al apagarse con SIGTERM (docker stop)

    app = FastAPI(title=f"Inventario ({settings.instance_name})", lifespan=lifespan)

    @app.get("/api/stock/{sku}")
    def stock(sku: str):
        if sku not in STOCK:
            raise HTTPException(404, f"No existe {sku}")
        return {"sku": sku, "available": STOCK[sku], "instance": settings.instance_name}

    @app.get("/health")
    def health(response: Response):
        if not state["healthy"]:
            response.status_code = 503
        return {"status": "UP" if state["healthy"] else "DOWN", "instance": settings.instance_name}

    @app.post("/admin/health")
    def change_health(change: HealthChange):
        """Caos: "enferma" la instancia. El proceso sigue vivo, pero el health check falla."""
        state["healthy"] = change.healthy
        return {"instance": settings.instance_name, "healthy": change.healthy}

    return app
