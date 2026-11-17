"""El MODELO DE DOMINIO de envíos. Fíjate en lo que NO hay aquí: libras, UBIGEO, EST=3, "19/11/26",
COD_RET, XML. Nada del transportista. Si mañana cambiamos de transportista, este archivo no cambia.
"""
from dataclasses import dataclass, field
from datetime import date
from decimal import Decimal
from enum import StrEnum
from typing import Protocol


class ShipmentStatus(StrEnum):
    CREATED = "CREATED"
    PICKED_UP = "PICKED_UP"
    IN_TRANSIT = "IN_TRANSIT"
    OUT_FOR_DELIVERY = "OUT_FOR_DELIVERY"
    DELIVERED = "DELIVERED"
    RETURNED = "RETURNED"
    EXCEPTION = "EXCEPTION"
    # El transportista dijo algo que no sabemos traducir. Es un estado de NUESTRO dominio ("no sabemos"),
    # no un código del legado que se filtra.
    UNKNOWN = "UNKNOWN"


class ServiceLevel(StrEnum):
    EXPRESS = "EXPRESS"
    STANDARD = "STANDARD"
    ECONOMY = "ECONOMY"


@dataclass(frozen=True)
class Destination:
    district: str
    address: str


@dataclass(frozen=True)
class Dispatch:
    tracking_code: str
    estimated_delivery: date


@dataclass(frozen=True)
class Tracking:
    status: ShipmentStatus
    estimated_delivery: date | None
    delivered_on: date | None
    note: str | None


@dataclass
class Shipment:
    id: str
    order_id: str
    weight_kg: Decimal
    service_level: ServiceLevel
    destination: Destination
    status: ShipmentStatus = ShipmentStatus.CREATED
    tracking_code: str | None = None
    estimated_delivery: date | None = None
    delivered_on: date | None = None
    carrier_note: str | None = None
    history: list[ShipmentStatus] = field(default_factory=list)

    def dispatched(self, dispatch: Dispatch) -> None:
        self.tracking_code = dispatch.tracking_code
        self.estimated_delivery = dispatch.estimated_delivery
        self.history.append(self.status)

    def update(self, tracking: Tracking) -> None:
        if tracking.status != self.status:
            self.history.append(tracking.status)
        self.status = tracking.status
        self.estimated_delivery = tracking.estimated_delivery or self.estimated_delivery
        self.delivered_on = tracking.delivered_on
        self.carrier_note = tracking.note

    def can_rate_delivery(self) -> bool:
        """Regla de negocio: el cliente puede calificar la entrega cuando el envío llegó."""
        return self.status is ShipmentStatus.DELIVERED


class ShippingError(Exception):
    """Errores de NUESTRO dominio. El ACL traduce los COD_RET del legado a estos."""


class DestinationNotCovered(ShippingError):
    pass


class WeightLimitExceeded(ShippingError):
    pass


class CarrierUnavailable(ShippingError):
    pass


class Carrier(Protocol):
    """El PUERTO: lo que el dominio necesita de un transportista, en sus términos."""

    def dispatch(self, shipment: Shipment) -> Dispatch: ...

    def track(self, tracking_code: str) -> Tracking: ...
