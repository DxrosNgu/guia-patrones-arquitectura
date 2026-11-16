"""Envíos como participante de una saga ORQUESTADA.

  comando            hace                                     responde
  ────────────────   ──────────────────────────────────────   ──────────────────────────────
  ScheduleShipment   programa el envío con el transportista   ShipmentScheduled | ShipmentFailed
                     · transportista caído → se REINTENTA aquí mismo (no se responde todavía)

No sabe nada de cancelaciones ni de pagos: el orquestador solo le pide un envío
DESPUÉS del pivote (el cobro), cuando ya no se cancela.
"""

import logging

from sqlalchemy import Connection, Engine, select

from app.carrier import Carrier, CarrierDown, CarrierRejected
from app.db import Tables
from saga_kit.envelope import TransientError, new_event
from saga_kit.inbox import first_time
from saga_kit.outbox import enqueue

log = logging.getLogger("shipping")
REPLIES = "saga.replies"


class ShippingParticipant:
    def __init__(self, engine: Engine, tables: Tables, carrier: Carrier):
        self.engine, self.t, self.carrier = engine, tables, carrier

    def handle(self, command: dict) -> str:
        if command["type"] != "ScheduleShipment":
            return "IGNORED"
        with self.engine.begin() as conn:
            if not first_time(conn, self.t.processed, command["eventId"]):
                return "DUPLICATE"
            return self._ship(conn, command["aggregateId"])

    def _ship(self, conn: Connection, order_id: str) -> str:
        existing = conn.execute(select(self.t.shipments.c.tracking_code)
                                .where(self.t.shipments.c.order_id == order_id)).scalar()
        if existing:
            # Comando reenviado por el orquestador: devolvemos el MISMO envío.
            self._reply(conn, "ShipmentScheduled", order_id, {"trackingCode": existing})
            return "ALREADY_SCHEDULED"
        try:
            tracking = self.carrier.schedule(order_id)
        except CarrierDown as error:
            raise TransientError(f"Pedido {order_id}: {error}") from error
        except CarrierRejected as error:
            self._reply(conn, "ShipmentFailed", order_id, {"reason": str(error)})
            return "FAILED"
        conn.execute(self.t.shipments.insert().values(order_id=order_id, status="SCHEDULED", tracking_code=tracking))
        self._reply(conn, "ShipmentScheduled", order_id, {"trackingCode": tracking})
        log.info("🚚 Pedido %s: envío %s programado", order_id, tracking)
        return "SCHEDULED"

    def _reply(self, conn: Connection, type_: str, order_id: str, payload: dict) -> None:
        enqueue(conn, self.t.outbox, REPLIES, new_event(type_, order_id, payload))
