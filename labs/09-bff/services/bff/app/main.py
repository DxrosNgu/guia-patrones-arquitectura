"""BFF web: el backend de UNA interfaz (la web). Habla el idioma de sus pantallas."""
import logging
from contextlib import asynccontextmanager
from typing import Literal

import httpx
from fastapi import FastAPI
from fastapi.responses import JSONResponse

from app.compose import OrdersUnavailable, my_orders
from app.config import Settings
from app.services import Services


def create_app(settings: Settings | None = None, services: Services | None = None) -> FastAPI:
    settings = settings or Settings()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)-5s [%(name)s] %(message)s")
    state: dict = {"services": services}

    @asynccontextmanager
    async def lifespan(_app: FastAPI):
        if state["services"] is None:
            client = httpx.AsyncClient()
            state["services"] = Services(client, settings.orders_url, settings.payments_url, settings.shipping_url,
                                         settings.call_timeout_s)
            yield
            await client.aclose()
        else:
            yield

    app = FastAPI(title="BFF web", lifespan=lifespan)

    # ⚠️ SOLO PARA EL LAB: el cliente llega como parámetro, y cualquiera puede pedir los pedidos de otro.
    # En prod, el BFF maneja el login (Authorization Code + PKCE contra Keycloak), guarda los tokens del lado
    # del servidor y le da al navegador solo una cookie de sesión (HttpOnly). Aquí el cliente saldría de esa
    # sesión (el "sub" o "preferred_username" del token), y el BFF llamaría a los servicios con
    # "Authorization: Bearer <token>". Ver el README, "Cómo sería el login en prod".
    @app.get("/bff/my-orders")
    async def my_orders_screen(customer: str, mode: Literal["parallel", "sequential"] = "parallel"):
        try:
            return await my_orders(state["services"], customer, mode)
        except OrdersUnavailable as error:
            return JSONResponse(status_code=503, content={"detail": str(error)})

    @app.get("/health")
    def health():
        return {"status": "UP"}

    return app
