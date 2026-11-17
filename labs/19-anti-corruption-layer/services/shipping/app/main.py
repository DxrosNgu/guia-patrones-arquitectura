"""API de envíos. La parte con ACL habla SOLO el idioma del dominio; la ingenua, el del legado."""
import logging
from decimal import Decimal

import httpx
from fastapi import FastAPI
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from app.acl.adapter import LegacyCarrierAdapter
from app.acl.client import LegacyCarrierClient
from app.config import Settings
from app.domain import (
    Carrier,
    CarrierUnavailable,
    Destination,
    DestinationNotCovered,
    ServiceLevel,
    Shipment,
    WeightLimitExceeded,
)
from app.naive import api as naive
from app.service import ShippingService


class CreateShipment(BaseModel):
    orderId: str
    weightKg: Decimal = Field(gt=0, max_digits=6, decimal_places=2)
    serviceLevel: ServiceLevel
    district: str
    address: str = Field(min_length=3)


def view(s: Shipment) -> dict:
    return {
        "id": s.id, "orderId": s.order_id, "trackingCode": s.tracking_code, "status": s.status,
        "serviceLevel": s.service_level, "weightKg": s.weight_kg,
        "destination": {"district": s.destination.district, "address": s.destination.address},
        "estimatedDelivery": s.estimated_delivery, "deliveredOn": s.delivered_on, "carrierNote": s.carrier_note,
        "canRateDelivery": s.can_rate_delivery(), "history": s.history,
    }


def create_app(settings: Settings | None = None, carrier: Carrier | None = None,
               http: httpx.Client | None = None) -> FastAPI:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)-5s [%(name)s] %(message)s")
    settings = settings or Settings()
    http = http or httpx.Client(base_url=settings.legacy_carrier_url, timeout=settings.carrier_timeout_s)
    service = ShippingService(carrier or LegacyCarrierAdapter(LegacyCarrierClient(http)))
    app = FastAPI(title="Envíos · con y sin Anti-Corruption Layer")

    @app.exception_handler(DestinationNotCovered)
    def not_covered(_, e):
        return JSONResponse({"code": "DESTINATION_NOT_COVERED", "message": str(e)}, status_code=422)

    @app.exception_handler(WeightLimitExceeded)
    def too_heavy(_, e):
        return JSONResponse({"code": "WEIGHT_LIMIT_EXCEEDED", "message": str(e)}, status_code=422)

    @app.exception_handler(CarrierUnavailable)
    def unavailable(_, e):
        return JSONResponse({"code": "CARRIER_UNAVAILABLE", "message": str(e)}, status_code=502)

    @app.post("/api/shipments", status_code=201)
    def create(request: CreateShipment):
        return view(service.create(request.orderId, request.weightKg, request.serviceLevel,
                                   Destination(request.district, request.address)))

    @app.get("/api/shipments/{shipment_id}")
    def get(shipment_id: str):
        shipment = service.refresh(shipment_id)
        if shipment is None:
            return JSONResponse({"message": "No existe"}, status_code=404)
        return view(shipment)

    app.include_router(naive.router(http))

    @app.get("/health")
    def health():
        return {"status": "UP"}

    return app
