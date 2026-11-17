"""Casos de uso de envíos. Solo conocen el dominio y el puerto Carrier: no saben que existe un legado."""
import uuid
from decimal import Decimal

from app.domain import Carrier, Destination, ServiceLevel, Shipment


class ShippingService:
    def __init__(self, carrier: Carrier):
        self.carrier = carrier
        self.shipments: dict[str, Shipment] = {}   # en memoria: el lab trata de la integración, no de la persistencia

    def create(self, order_id: str, weight_kg: Decimal, service_level: ServiceLevel, destination: Destination) -> Shipment:
        shipment = Shipment(id=str(uuid.uuid4()), order_id=order_id, weight_kg=weight_kg,
                            service_level=service_level, destination=destination)
        shipment.dispatched(self.carrier.dispatch(shipment))
        self.shipments[shipment.id] = shipment
        return shipment

    def refresh(self, shipment_id: str) -> Shipment | None:
        shipment = self.shipments.get(shipment_id)
        if shipment is not None and shipment.tracking_code:
            shipment.update(self.carrier.track(shipment.tracking_code))
        return shipment
