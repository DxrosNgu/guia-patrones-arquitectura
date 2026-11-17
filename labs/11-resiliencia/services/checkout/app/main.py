"""Checkout: el paso "Pagar" de la web. Llama a pagos con reintentos (tenacity)."""
import logging
from contextlib import asynccontextmanager

import httpx
from fastapi import FastAPI
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from pydantic_settings import BaseSettings

from app.payments import PaymentsClient, TransientHttpError


class Settings(BaseSettings):
    payments_url: str = "http://localhost:8104"
    retry_attempts: int = 3
    call_timeout_s: float = 2.0


class Checkout(BaseModel):
    orderId: str  # noqa: N815
    amount: float


def create_app(settings: Settings | None = None, payments: PaymentsClient | None = None) -> FastAPI:
    settings = settings or Settings()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)-5s [%(name)s] %(message)s")
    state = {"payments": payments}

    @asynccontextmanager
    async def lifespan(_app: FastAPI):
        if state["payments"] is None:
            async with httpx.AsyncClient() as client:
                state["payments"] = PaymentsClient(client, settings.payments_url, settings.retry_attempts,
                                                   settings.call_timeout_s)
                yield
        else:
            yield

    app = FastAPI(title="Checkout", lifespan=lifespan)

    @app.post("/api/checkout")
    async def checkout(request: Checkout):
        client: PaymentsClient = state["payments"]
        try:
            response, attempts = await client.pay(request.model_dump())
        except (httpx.TransportError, TransientHttpError) as error:
            return JSONResponse(status_code=503, content={
                "detail": f"Pagos no respondió después de {client.attempts} intentos: {error}",
                "attempts": client.attempts})
        return JSONResponse(status_code=response.status_code,
                            content={"payment": response.json(), "attempts": attempts})

    @app.get("/health")
    def health():
        return {"status": "UP"}

    return app
