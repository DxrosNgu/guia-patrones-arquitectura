"""API Composition para la pantalla "Mis pedidos".

1. Pide los pedidos del cliente (sin pedidos no hay pantalla: si falla, 503).
2. Pide pagos y envíos de esos pedidos, EN PARALELO y con timeout.
3. Arma una respuesta a la medida de la pantalla: textos listos, solo los campos que se muestran.
   Si pagos o envíos no responden, la respuesta es PARCIAL (y lo dice), no un error.
"""
import asyncio
import logging
import time
from collections.abc import Awaitable

import httpx

from app.services import Services

log = logging.getLogger("bff")

PAYMENT_LABELS = {"PAID": "Pagado", "PENDING": "Pago pendiente", "REFUNDED": "Reembolsado"}
SHIPMENT_LABELS = {"PREPARING": "Preparando", "IN_TRANSIT": "En camino", "DELIVERED": "Entregado"}


class OrdersUnavailable(Exception):
    """Sin la lista de pedidos no hay nada que componer."""


async def _optional(name: str, call: Awaitable[list], unavailable: list[str]) -> list | None:
    try:
        return await call
    except (httpx.HTTPError, TimeoutError) as error:
        log.warning("%s no respondió (%s): la respuesta será parcial", name, type(error).__name__)
        unavailable.append(name)
        return None


def _by_order(rows: list | None) -> dict | None:
    return None if rows is None else {row["orderId"]: row for row in rows}


def _order_view(order: dict, payments: dict | None, shipments: dict | None) -> dict:
    if payments is None:
        payment = "Estado de pago no disponible"
    else:
        found = payments.get(order["id"])
        payment = PAYMENT_LABELS.get(found["status"], found["status"]) if found else "Sin pago"

    shipment_row = shipments.get(order["id"]) if shipments is not None else None
    if shipments is None:
        shipment = "Estado de envío no disponible"
    elif shipment_row is None:
        shipment = "Aún no se envía"
    else:
        shipment = SHIPMENT_LABELS.get(shipment_row["status"], shipment_row["status"])

    return {
        "id": order["id"],
        "date": order["createdAt"][:10],
        "total": order["total"],
        "summary": ", ".join(f"{i['quantity']} × {i['name']}" for i in order["items"]),
        "payment": payment,
        "shipment": shipment,
        "tracking": shipment_row.get("trackingCode") if shipment_row else None,
        "eta": shipment_row.get("eta") if shipment_row else None,
    }


async def my_orders(services: Services, customer: str, mode: str = "parallel") -> dict:
    start = time.perf_counter()
    try:
        orders = await services.orders(customer)
    except (httpx.HTTPError, TimeoutError) as error:
        raise OrdersUnavailable(f"Pedidos no respondió: {type(error).__name__}") from error

    ids = [order["id"] for order in orders]
    unavailable: list[str] = []
    if not ids:
        payments, shipments = [], []
    elif mode == "sequential":
        # Una después de otra: el tiempo total es la SUMA.
        payments = await _optional("payments", services.payments(ids), unavailable)
        shipments = await _optional("shipping", services.shipments(ids), unavailable)
    else:
        # A la vez: el tiempo total es el de la MÁS LENTA.
        payments, shipments = await asyncio.gather(
            _optional("payments", services.payments(ids), unavailable),
            _optional("shipping", services.shipments(ids), unavailable))

    payments_by_order, shipments_by_order = _by_order(payments), _by_order(shipments)
    return {
        "customer": customer,
        "mode": mode,
        "orders": [_order_view(o, payments_by_order, shipments_by_order) for o in orders],
        "unavailable": sorted(unavailable),
        "tookMs": round((time.perf_counter() - start) * 1000),
    }
