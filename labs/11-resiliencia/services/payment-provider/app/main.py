"""Pasarela de pagos EXTERNA (simulada). No es nuestra: no la podemos arreglar, solo protegernos de ella.

- Idempotente por `idempotencyKey`: el mismo cobro repetido no cobra dos veces (lab 12).
- Montos > 1000 se rechazan (402): es un error de NEGOCIO, reintentarlo no sirve.
- Controles de caos (PUT /admin/chaos): latencia, porcentaje de errores y caída total.
- /admin/stats cuenta cuántas peticiones RECIBIÓ: así se ve el "retry storm".
"""
import asyncio
import logging
import random
import threading
import uuid

from fastapi import FastAPI
from fastapi.responses import JSONResponse
from pydantic import BaseModel

log = logging.getLogger("payment-provider")
DECLINE_ABOVE = 1000.0


class ChargeRequest(BaseModel):
    idempotencyKey: str  # noqa: N815 (JSON de la pasarela)
    orderId: str  # noqa: N815
    amount: float


class Chaos(BaseModel):
    latencyMs: int = 0  # noqa: N815
    errorRate: float = 0.0  # noqa: N815
    down: bool = False


def create_app() -> FastAPI:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)-5s [%(name)s] %(message)s")
    app = FastAPI(title="Pasarela de pagos (simulada)")
    lock = threading.Lock()
    state = {"chaos": Chaos(), "charges": {}, "requests": 0, "rejected": 0}

    @app.post("/charges")
    async def charge(request: ChargeRequest):
        with lock:
            state["requests"] += 1
        chaos: Chaos = state["chaos"]
        if chaos.down:
            return JSONResponse(status_code=503, content={"error": "pasarela fuera de servicio"})
        if chaos.latencyMs:
            await asyncio.sleep(chaos.latencyMs / 1000)
        if random.random() < chaos.errorRate:
            return JSONResponse(status_code=500, content={"error": "error interno de la pasarela"})
        if request.amount > DECLINE_ABOVE:
            with lock:
                state["rejected"] += 1
            return JSONResponse(status_code=402, content={"status": "DECLINED", "reason": "fondos insuficientes"})
        with lock:
            existing = state["charges"].get(request.idempotencyKey)
            if existing is None:
                existing = {"chargeId": f"ch_{uuid.uuid4().hex[:12]}", "orderId": request.orderId,
                            "amount": request.amount, "status": "APPROVED"}
                state["charges"][request.idempotencyKey] = existing
                replayed = False
            else:
                replayed = True
        return JSONResponse(status_code=201, content={**existing, "replayed": replayed})

    @app.get("/charges/{key}")
    def charge_status(key: str):
        found = state["charges"].get(key)
        if found is None:
            return JSONResponse(status_code=404, content={"error": "no existe"})
        return found

    @app.get("/admin/chaos")
    def get_chaos():
        return state["chaos"]

    @app.put("/admin/chaos")
    def set_chaos(chaos: Chaos):
        state["chaos"] = chaos
        log.warning("Caos: %s", chaos.model_dump())
        return chaos

    @app.get("/admin/stats")
    def stats():
        return {"requests": state["requests"], "charges": len(state["charges"]), "rejected": state["rejected"]}

    @app.post("/admin/reset")
    def reset():
        with lock:
            state.update(chaos=Chaos(), charges={}, requests=0, rejected=0)
        return {"reset": True}

    @app.get("/health")
    def health():
        return {"status": "UP"}

    return app
