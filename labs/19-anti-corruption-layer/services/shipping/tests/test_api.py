from datetime import date

from fastapi.testclient import TestClient

from app.domain import (
    CarrierUnavailable,
    DestinationNotCovered,
    Dispatch,
    Shipment,
    ShipmentStatus,
    Tracking,
)
from app.main import create_app


class FakeCarrier:
    """El puerto, implementado en memoria: los tests del servicio no saben que existe un legado."""

    def __init__(self):
        self.status = ShipmentStatus.CREATED
        self.fail: Exception | None = None

    def dispatch(self, shipment: Shipment) -> Dispatch:
        if self.fail:
            raise self.fail
        return Dispatch("T-1", date(2026, 11, 19))

    def track(self, tracking_code: str) -> Tracking:
        delivered = date(2026, 11, 18) if self.status is ShipmentStatus.DELIVERED else None
        return Tracking(self.status, date(2026, 11, 19), delivered, None)


BODY = {"orderId": "o-1", "weightKg": "2.00", "serviceLevel": "EXPRESS", "district": "Miraflores",
        "address": "Av. Larco 1150"}


def test_the_api_speaks_only_the_domain_language():
    carrier = FakeCarrier()
    client = TestClient(create_app(carrier=carrier))

    created = client.post("/api/shipments", json=BODY)
    assert created.status_code == 201
    carrier.status = ShipmentStatus.DELIVERED
    body = client.get(f"/api/shipments/{created.json()['id']}").json()

    assert body["status"] == "DELIVERED"
    assert body["canRateDelivery"] is True
    assert body["estimatedDelivery"] == "2026-11-19"
    assert body["history"] == ["CREATED", "DELIVERED"]
    legacy_words = {"EST", "FEC_EST", "PESO_LB", "UBIGEO", "TPO_SRV", "COD_RET", "NRO_GUIA"}
    assert legacy_words.isdisjoint(body) and "lb" not in str(body).lower()


def test_domain_errors_have_their_own_http_status():
    carrier = FakeCarrier()
    client = TestClient(create_app(carrier=carrier))

    carrier.fail = DestinationNotCovered("Arequipa")
    assert client.post("/api/shipments", json=BODY).json()["code"] == "DESTINATION_NOT_COVERED"
    carrier.fail = CarrierUnavailable("timeout")
    assert client.post("/api/shipments", json=BODY).status_code == 502


def test_unknown_shipment_is_404():
    assert TestClient(create_app(carrier=FakeCarrier())).get("/api/shipments/nope").status_code == 404
