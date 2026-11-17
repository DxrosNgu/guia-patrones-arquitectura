"""El ADAPTADOR: implementa el puerto Carrier del dominio usando el cliente y el traductor.

Es la única puerta entre los dos mundos. Del lado de adentro solo salen objetos de NUESTRO dominio
(Dispatch, Tracking, ShippingError); del lado de afuera quedan el XML, los COD_RET y los EST.
"""
import logging

from app.acl import translator
from app.acl.client import LegacyCarrierClient
from app.acl.translator import LegacyError, UnknownLegacyCode
from app.domain import (
    CarrierUnavailable,
    DestinationNotCovered,
    Dispatch,
    Shipment,
    ShipmentStatus,
    Tracking,
    WeightLimitExceeded,
)

log = logging.getLogger("acl")

# COD_RET del legado → errores de nuestro dominio.
ERRORS = {"07": WeightLimitExceeded, "12": DestinationNotCovered}


class LegacyCarrierAdapter:
    def __init__(self, client: LegacyCarrierClient):
        self.client = client

    def dispatch(self, shipment: Shipment) -> Dispatch:
        body = self.client.post_dispatch(translator.to_dispatch_request(shipment))
        try:
            return translator.parse_dispatch_response(body)
        except LegacyError as e:
            raise ERRORS.get(e.code, CarrierUnavailable)(str(e)) from e

    def track(self, tracking_code: str) -> Tracking:
        body = self.client.get_tracking(tracking_code)
        try:
            return translator.parse_tracking_response(body)
        except UnknownLegacyCode as e:
            # El legado cambió y nadie nos avisó. No dejamos pasar su código: el dominio recibe UNKNOWN
            # y queda un WARNING (y el contract check falla). Arreglarlo es agregar una fila en translator.py.
            log.warning("Código del transportista sin traducir: %s (guía %s)", e, tracking_code)
            return Tracking(status=ShipmentStatus.UNKNOWN, estimated_delivery=None, delivered_on=None,
                            note=f"Estado del transportista sin traducir: {e}")
        except LegacyError as e:
            raise CarrierUnavailable(str(e)) from e
