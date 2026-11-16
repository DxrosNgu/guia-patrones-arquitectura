"""Envíos como participante de la saga por COREOGRAFÍA.

El pago es el PIVOTE de la saga: después de él, la saga intenta AVANZAR.

  escucha            hace                                       publica
  ────────────────   ────────────────────────────────────────   ───────────────────────────────
  PaymentCompleted   programa el envío con el transportista     ShipmentScheduled
                     · transportista caído → REINTENTAR (S4)    (nada: se reintenta el evento)
                     · rechazo definitivo  → COMPENSAR  (S5)    ShipmentFailed
  OrderCancelled     recuerda la cancelación; anula el envío    —
"""

import logging

from sqlalchemy import Connection, Engine, select, update
from sqlalchemy.dialects.postgresql import insert

from app.carrier import Carrier, CarrierDown, CarrierRejected
from app.db import Tables
from saga_kit.envelope import TransientError, new_event
from saga_kit.inbox import first_time
from saga_kit.outbox import enqueue

log = logging.getLogger("shipping")
TOPIC = "shipping.events"


class ShippingParticipant:
    def __init__(self, engine: Engine, tables: Tables, carrier: Carrier):
        self.engine, self.t, self.carrier = engine, tables, carrier

    def handle(self, event: dict) -> str:
        handlers = {"PaymentCompleted": self._ship, "OrderCancelled": self._cancel}
        handler = handlers.get(event["type"])
        if handler is None:
            return "IGNORED"
        # Si el handler lanza TransientError, la transacción se deshace ENTERA (incluido el
        # registro en processed_messages): el mismo evento se podrá procesar en el reintento.
        with self.engine.begin() as conn:
            if not first_time(conn, self.t.processed, event["eventId"]):
                return "DUPLICATE"
            return handler(conn, event)

    def _ship(self, conn: Connection, event: dict) -> str:
        order_id = event["aggregateId"]
        if self._is_cancelled(conn, order_id):
            log.info("Pedido %s cancelado: no se envía", order_id)
            return "IGNORED_CANCELLED"
        try:
            tracking = self.carrier.schedule(order_id)
        except CarrierDown as error:
            raise TransientError(f"Pedido {order_id}: {error}") from error
        except CarrierRejected as error:
            enqueue(conn, self.t.outbox, TOPIC, new_event("ShipmentFailed", order_id, {"reason": str(error)}))
            log.info("❌ Pedido %s: %s", order_id, error)
            return "FAILED"

        conn.execute(self.t.shipments.insert().values(order_id=order_id, status="SCHEDULED", tracking_code=tracking))
        enqueue(conn, self.t.outbox, TOPIC, new_event("ShipmentScheduled", order_id,
                                                      {"trackingCode": tracking, "carrier": "Transportes Simulados"}))
        log.info("🚚 Pedido %s: envío %s programado", order_id, tracking)
        return "SCHEDULED"

    def _cancel(self, conn: Connection, event: dict) -> str:
        order_id = event["aggregateId"]
        conn.execute(insert(self.t.cancellations).values(order_id=order_id)
                     .on_conflict_do_nothing(index_elements=["order_id"]))
        conn.execute(update(self.t.shipments).where(self.t.shipments.c.order_id == order_id)
                     .values(status="CANCELLED"))
        return "CANCELLED"

    def _is_cancelled(self, conn: Connection, order_id: str) -> bool:
        return conn.execute(select(self.t.cancellations.c.order_id)
                            .where(self.t.cancellations.c.order_id == order_id)).first() is not None
