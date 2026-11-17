"""El TRADUCTOR del Anti-Corruption Layer: funciones puras entre el modelo del legado y el nuestro.

Todo lo que sabemos del transportista "Rápido SAC" está en ESTE archivo: sus códigos, sus unidades,
sus formatos de fecha y su XML. Si el legado cambia, se cambia aquí y en ningún otro lado.
"""
import unicodedata
from datetime import date, datetime
from decimal import ROUND_HALF_UP, Decimal
from xml.etree.ElementTree import Element, SubElement, tostring

from defusedxml.ElementTree import fromstring

from app.domain import (
    DestinationNotCovered,
    Dispatch,
    ServiceLevel,
    Shipment,
    ShipmentStatus,
    Tracking,
)

# ── Las tablas de traducción ─────────────────────────────────────────────

STATUS_BY_EST: dict[str, ShipmentStatus] = {
    "1": ShipmentStatus.CREATED,       # REGISTRADO
    "2": ShipmentStatus.PICKED_UP,     # RECOGIDO
    "3": ShipmentStatus.IN_TRANSIT,    # EN RUTA
    "4": ShipmentStatus.DELIVERED,     # ENTREGADO
    "5": ShipmentStatus.RETURNED,      # DEVUELTO
    "9": ShipmentStatus.EXCEPTION,     # INCIDENCIA
}

SERVICE_CODE: dict[ServiceLevel, str] = {
    ServiceLevel.EXPRESS: "EXP",
    ServiceLevel.STANDARD: "STD",
    ServiceLevel.ECONOMY: "ECO",
}

# UBIGEO: código de distrito del INEI. El transportista solo cubre estos.
UBIGEO_BY_DISTRICT: dict[str, str] = {
    "lima": "150101",
    "barranco": "150104",
    "miraflores": "150122",
    "san isidro": "150131",
    "santiago de surco": "150140",
    "surco": "150140",
}

LB_PER_KG = Decimal("2.20462")
MAX_ADDRESS = 40


class LegacyError(Exception):
    """El legado respondió COD_RET distinto de 00 (con HTTP 200)."""

    def __init__(self, code: str, message: str):
        super().__init__(f"COD_RET={code} {message}")
        self.code = code


class UnknownLegacyCode(Exception):
    """Un código que nuestras tablas no conocen: el legado cambió."""


# ── Nuestro modelo → el del legado ───────────────────────────────────────

def to_dispatch_request(shipment: Shipment) -> bytes:
    ubigeo = UBIGEO_BY_DISTRICT.get(_normalize(shipment.destination.district).lower())
    if ubigeo is None:
        raise DestinationNotCovered(f"El transportista no llega a {shipment.destination.district}")
    root = Element("SOLICITUD")
    SubElement(root, "TPO_SRV").text = SERVICE_CODE[shipment.service_level]
    SubElement(root, "PESO_LB").text = kg_to_legacy_pounds(shipment.weight_kg)
    SubElement(root, "UBIGEO").text = ubigeo
    SubElement(root, "DIR").text = legacy_address(shipment.destination.address)
    SubElement(root, "REF").text = shipment.order_id
    return tostring(root, encoding="ISO-8859-1", xml_declaration=True)


def kg_to_legacy_pounds(kg: Decimal) -> str:
    """2 kg → "4,41": libras, 2 decimales, coma decimal."""
    return str((kg * LB_PER_KG).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)).replace(".", ",")


def legacy_address(address: str) -> str:
    """"Av. Larco 1150, dpto. 302" → "AV. LARCO 1150, DPTO. 302": mayúsculas, sin tildes (la Ñ sí), 40 caracteres."""
    return _normalize(address).upper()[:MAX_ADDRESS].strip()


def _normalize(text: str) -> str:
    protected = text.replace("ñ", "\0").replace("Ñ", "\1")
    plain = "".join(c for c in unicodedata.normalize("NFKD", protected) if not unicodedata.combining(c))
    return " ".join(plain.replace("\0", "ñ").replace("\1", "Ñ").split())


# ── El modelo del legado → el nuestro ────────────────────────────────────

def parse_dispatch_response(body: bytes) -> Dispatch:
    fields = _fields(body)
    return Dispatch(tracking_code=fields["NRO_GUIA"], estimated_delivery=legacy_date(fields["FEC_EST"]))


def parse_tracking_response(body: bytes) -> Tracking:
    fields = _fields(body)
    est = fields.get("EST", "")
    if est not in STATUS_BY_EST:
        raise UnknownLegacyCode(f"EST={est} ({fields.get('DES_EST', '?')})")
    return Tracking(status=STATUS_BY_EST[est], estimated_delivery=legacy_date(fields.get("FEC_EST")),
                    delivered_on=legacy_date(fields.get("FEC_ENT")),
                    # OBS es texto libre para personas, no un código: pasa tal cual (ya decodificado de Latin-1).
                    note=fields.get("OBS", "").strip() or None)


def legacy_date(text: str | None) -> date | None:
    """"19/11/26" → date(2026, 11, 19). Vacío → None."""
    return datetime.strptime(text.strip(), "%d/%m/%y").date() if text and text.strip() else None


def _fields(body: bytes) -> dict[str, str]:
    """Lee el XML (el encoding lo dice su declaración: ISO-8859-1) y valida COD_RET."""
    root = fromstring(body)
    fields = {child.tag: (child.text or "") for child in root}
    if fields.get("COD_RET") != "00":
        raise LegacyError(fields.get("COD_RET", "??"), fields.get("DES_RET", ""))
    return fields
