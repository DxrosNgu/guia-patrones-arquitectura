"""CONTRACT CHECK: ¿el legado sigue hablando como el traductor espera?

    python -m app.acl.contract_check

Despacha un envío de prueba y lo hace avanzar por todos sus estados (con la API de administración del
SIMULADOR; con un transportista real se usa su ambiente de pruebas). Cada respuesta pasa por el MISMO
traductor que usa el servicio. Si algún código, fecha o formato no se puede traducir, falla con exit 1.
Pensado para correr en CI contra el ambiente de pruebas del transportista, o cada noche.
"""
import sys
from datetime import date
from decimal import Decimal

import httpx

from app.acl import translator
from app.acl.client import LegacyCarrierClient
from app.acl.translator import UnknownLegacyCode
from app.config import Settings
from app.domain import Destination, ServiceLevel, Shipment, ShipmentStatus

SAMPLE = Shipment(id="contract-check", order_id="contract-check", weight_kg=Decimal("2.00"),
                  service_level=ServiceLevel.EXPRESS, destination=Destination("Miraflores", "Av. Larco 1150"))


def check(http: httpx.Client) -> list[str]:
    client = LegacyCarrierClient(http)
    problems: list[str] = []
    dispatch = translator.parse_dispatch_response(client.post_dispatch(translator.to_dispatch_request(SAMPLE)))
    print(f"✔ despacho: guía {dispatch.tracking_code}, entrega estimada {dispatch.estimated_delivery}")
    seen: list[ShipmentStatus] = []
    for _ in range(10):
        try:
            tracking = translator.parse_tracking_response(client.get_tracking(dispatch.tracking_code))
        except UnknownLegacyCode as e:
            problems.append(f"código sin traducir: {e}")
            print(f"✖ {e} → no está en STATUS_BY_EST (translator.py)")
        else:
            seen.append(tracking.status)
            print(f"✔ estado → {tracking.status}")
            if tracking.status is ShipmentStatus.DELIVERED:
                if not isinstance(tracking.delivered_on, date):
                    problems.append("ENTREGADO sin fecha de entrega legible")
                break
        http.post(f"/admin/guias/{dispatch.tracking_code}/avanzar").raise_for_status()
    if ShipmentStatus.DELIVERED not in seen:
        problems.append("el envío de prueba nunca llegó a DELIVERED")
    return problems


if __name__ == "__main__":
    with httpx.Client(base_url=Settings().legacy_carrier_url, timeout=5) as http:
        found = check(http)
    if found:
        print(f"❌ El contrato con el transportista se rompió ({len(found)}): " + "; ".join(found))
        sys.exit(1)
    print("✅ El transportista sigue hablando como el traductor espera")
