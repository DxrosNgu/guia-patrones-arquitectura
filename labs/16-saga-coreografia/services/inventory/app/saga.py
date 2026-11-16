"""Inventario como participante de la saga por COREOGRAFÍA.

No hay nadie que le dé órdenes: reacciona a eventos de otros y publica los suyos.

  escucha             hace                                  publica
  ─────────────────   ───────────────────────────────────   ────────────────────────────────────
  OrderCreated        reserva el stock (todo o nada)        StockReserved | StockReservationFailed
  PaymentFailed       COMPENSA: libera la reserva            StockReleased
  ShipmentFailed      COMPENSA: libera la reserva            StockReleased
  OrderCancelled      recuerda la cancelación y libera       StockReleased (si había reserva)

Cada evento se procesa en UNA transacción: inbox (idempotencia) + cambio + outbox.
"""

import logging

from sqlalchemy import Connection, Engine, select, update
from sqlalchemy.dialects.postgresql import insert

from app.db import Tables
from saga_kit.envelope import new_event
from saga_kit.inbox import first_time
from saga_kit.outbox import enqueue

log = logging.getLogger("inventory")
TOPIC = "inventory.events"


class _OutOfStock(Exception):
    pass


class InventoryParticipant:
    def __init__(self, engine: Engine, tables: Tables):
        self.engine, self.t = engine, tables

    def handle(self, event: dict) -> str:
        handlers = {
            "OrderCreated": self._reserve,
            "PaymentFailed": lambda c, e: self._release(c, e["aggregateId"], "pago rechazado"),
            "ShipmentFailed": lambda c, e: self._release(c, e["aggregateId"], "envío imposible"),
            "OrderCancelled": self._cancel,
        }
        handler = handlers.get(event["type"])
        if handler is None:
            return "IGNORED"
        with self.engine.begin() as conn:
            if not first_time(conn, self.t.processed, event["eventId"]):
                log.info("↩️  %s %s ya procesado", event["type"], event["eventId"])
                return "DUPLICATE"
            return handler(conn, event)

    # ── reservar ─────────────────────────────────────────────────────────
    def _reserve(self, conn: Connection, event: dict) -> str:
        order_id, payload = event["aggregateId"], event["payload"]
        if self._is_cancelled(conn, order_id):
            log.info("Pedido %s ya estaba cancelado: no se reserva", order_id)
            return "IGNORED_CANCELLED"
        try:
            with conn.begin_nested():   # SAVEPOINT: si falta un ítem, nada se descuenta
                for item in payload["items"]:
                    # FOR UPDATE: si dos sagas quieren la última unidad, la segunda ESPERA y luego ve 0 (S12).
                    current = conn.execute(select(self.t.stock.c.available)
                                           .where(self.t.stock.c.sku == item["sku"]).with_for_update()).scalar()
                    if current is None or current < item["quantity"]:
                        raise _OutOfStock(f"Stock insuficiente para {item['sku']}: hay {current or 0}")
                    conn.execute(update(self.t.stock).where(self.t.stock.c.sku == item["sku"])
                                 .values(available=self.t.stock.c.available - item["quantity"]))
                    conn.execute(self.t.reservations.insert().values(
                        order_id=order_id, sku=item["sku"], quantity=item["quantity"], status="RESERVED"))
        except _OutOfStock as error:
            enqueue(conn, self.t.outbox, TOPIC, new_event("StockReservationFailed", order_id, {"reason": str(error)}))
            log.info("Pedido %s: %s", order_id, error)
            return "FAILED"

        enqueue(conn, self.t.outbox, TOPIC, new_event("StockReserved", order_id, {
            "customerId": payload["customerId"], "items": payload["items"],
            "total": payload["total"], "currency": payload["currency"]}))
        log.info("Pedido %s: stock reservado", order_id)
        return "RESERVED"

    # ── compensar ────────────────────────────────────────────────────────
    def _release(self, conn: Connection, order_id: str, reason: str) -> str:
        """Compensación IDEMPOTENTE: si ya se liberó (o nunca se reservó), no hace nada."""
        rows = conn.execute(select(self.t.reservations)
                            .where(self.t.reservations.c.order_id == order_id,
                                   self.t.reservations.c.status == "RESERVED")
                            .with_for_update()).all()
        if not rows:
            return "NOTHING_TO_RELEASE"
        for row in rows:
            conn.execute(update(self.t.stock).where(self.t.stock.c.sku == row.sku)
                         .values(available=self.t.stock.c.available + row.quantity))
            conn.execute(update(self.t.reservations).where(self.t.reservations.c.id == row.id)
                         .values(status="RELEASED"))
        enqueue(conn, self.t.outbox, TOPIC, new_event("StockReleased", order_id, {"reason": reason}))
        log.info("↩️  Pedido %s: stock liberado (%s)", order_id, reason)
        return "RELEASED"

    def _cancel(self, conn: Connection, event: dict) -> str:
        conn.execute(insert(self.t.cancellations).values(order_id=event["aggregateId"])
                     .on_conflict_do_nothing(index_elements=["order_id"]))
        return self._release(conn, event["aggregateId"], "pedido cancelado")

    def _is_cancelled(self, conn: Connection, order_id: str) -> bool:
        return conn.execute(select(self.t.cancellations.c.order_id)
                            .where(self.t.cancellations.c.order_id == order_id)).first() is not None
