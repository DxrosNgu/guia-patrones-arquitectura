import logging
from decimal import Decimal
from pathlib import Path

import pytest

from app.acl.adapter import LegacyCarrierAdapter
from app.domain import (
    Destination,
    ServiceLevel,
    Shipment,
    ShipmentStatus,
    WeightLimitExceeded,
)

FIXTURES = Path(__file__).parent / "fixtures"


class FakeLegacyClient:
    """Devuelve respuestas guardadas del legado, sin red."""

    def __init__(self, dispatch: str = "despacho_ok.xml", tracking: str = "consulta_en_ruta.xml"):
        self.dispatch, self.tracking = dispatch, tracking
        self.sent: list[bytes] = []

    def post_dispatch(self, body: bytes) -> bytes:
        self.sent.append(body)
        return (FIXTURES / self.dispatch).read_bytes()

    def get_tracking(self, guide: str) -> bytes:
        return (FIXTURES / self.tracking).read_bytes()


SHIPMENT = Shipment(id="s-1", order_id="o-1", weight_kg=Decimal("2.00"), service_level=ServiceLevel.STANDARD,
                    destination=Destination("Barranco", "Av. Grau 300"))


def test_dispatch_sends_the_translated_request_and_returns_our_dispatch():
    client = FakeLegacyClient()

    dispatch = LegacyCarrierAdapter(client).dispatch(SHIPMENT)

    assert dispatch.tracking_code == "RS0000042"
    assert b"<TPO_SRV>STD</TPO_SRV>" in client.sent[0]


def test_a_legacy_return_code_becomes_a_domain_error():
    with pytest.raises(WeightLimitExceeded):
        LegacyCarrierAdapter(FakeLegacyClient(dispatch="despacho_peso_excedido.xml")).dispatch(SHIPMENT)


def test_an_unknown_legacy_code_never_leaks_into_the_domain(caplog):
    with caplog.at_level(logging.WARNING, logger="acl"):
        tracking = LegacyCarrierAdapter(FakeLegacyClient(tracking="consulta_v2_en_transito.xml")).track("RS0000042")

    assert tracking.status is ShipmentStatus.UNKNOWN
    assert "EST=7" in caplog.text
