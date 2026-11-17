"""Tests del TRADUCTOR con las respuestas guardadas del legado (tests/fixtures): el contrato, escrito."""
from datetime import date
from decimal import Decimal
from pathlib import Path

import pytest
from defusedxml.ElementTree import fromstring

from app.acl import translator
from app.acl.translator import LegacyError, UnknownLegacyCode
from app.domain import (
    Destination,
    DestinationNotCovered,
    ServiceLevel,
    Shipment,
    ShipmentStatus,
)

FIXTURES = Path(__file__).parent / "fixtures"


def fixture(name: str) -> bytes:
    return (FIXTURES / name).read_bytes()


def shipment(district="Miraflores", address="Av. Larco 1150, dpto. 302", kg="2.00") -> Shipment:
    return Shipment(id="s-1", order_id="o-1", weight_kg=Decimal(kg), service_level=ServiceLevel.EXPRESS,
                    destination=Destination(district, address))


def test_our_shipment_becomes_the_legacy_request():
    xml = translator.to_dispatch_request(shipment())

    assert xml.startswith(b"<?xml version='1.0' encoding='ISO-8859-1'?>")
    fields = {c.tag: c.text for c in fromstring(xml)}
    assert fields == {"TPO_SRV": "EXP", "PESO_LB": "4,41", "UBIGEO": "150122",
                      "DIR": "AV. LARCO 1150, DPTO. 302", "REF": "o-1"}


@pytest.mark.parametrize("kg, pounds", [("2.00", "4,41"), ("0.5", "1,10"), ("49.9", "110,01")])
def test_kilograms_become_pounds_with_decimal_comma(kg, pounds):
    assert translator.kg_to_legacy_pounds(Decimal(kg)) == pounds


def test_the_address_is_uppercase_without_accents_but_keeps_the_enye_and_fits_40_chars():
    assert translator.legacy_address("Jr. Ñaña  Peña 123, Miraflores") == "JR. ÑAÑA PEÑA 123, MIRAFLORES"
    assert translator.legacy_address("Avenida José Gálvez Barrenechea 1234, oficina 5") == "AVENIDA JOSE GALVEZ BARRENECHEA 1234, OF"


def test_a_district_name_becomes_its_ubigeo_and_unknown_districts_are_a_domain_error():
    assert b"<UBIGEO>150140</UBIGEO>" in translator.to_dispatch_request(shipment(district="Santiago de Surco"))
    assert b"<UBIGEO>150131</UBIGEO>" in translator.to_dispatch_request(shipment(district="San Isidro"))
    with pytest.raises(DestinationNotCovered):
        translator.to_dispatch_request(shipment(district="Arequipa"))


def test_dispatch_response_becomes_our_dispatch():
    dispatch = translator.parse_dispatch_response(fixture("despacho_ok.xml"))

    assert dispatch.tracking_code == "RS0000042"
    assert dispatch.estimated_delivery == date(2026, 11, 19)


def test_a_legacy_error_comes_as_a_return_code():
    with pytest.raises(LegacyError) as e:
        translator.parse_dispatch_response(fixture("despacho_peso_excedido.xml"))
    assert e.value.code == "07"


def test_tracking_codes_become_our_statuses():
    in_route = translator.parse_tracking_response(fixture("consulta_en_ruta.xml"))
    delivered = translator.parse_tracking_response(fixture("consulta_entregado.xml"))

    assert in_route.status is ShipmentStatus.IN_TRANSIT
    assert in_route.delivered_on is None
    assert delivered.status is ShipmentStatus.DELIVERED
    assert delivered.delivered_on == date(2026, 11, 18)
    assert delivered.note == "RECIBIDO POR: SRA. MUÑOZ"     # la Ñ llegó bien desde ISO-8859-1


def test_a_code_the_translator_does_not_know_is_an_explicit_error():
    with pytest.raises(UnknownLegacyCode, match="EST=7"):
        translator.parse_tracking_response(fixture("consulta_v2_en_transito.xml"))
