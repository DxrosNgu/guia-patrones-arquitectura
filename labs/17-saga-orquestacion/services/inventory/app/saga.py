"""Inventario como participante de una saga ORQUESTADA.

Ya no escucha eventos de otros servicios: recibe COMANDOS del orquestador
(topic inventory.commands) y le RESPONDE (topic saga.replies). No sabe que
existen pagos ni envíos. Compáralo con el lab 16.

  comando        hace                                   responde
  ────────────   ────────────────────────────────────   ──────────────────────────────────────
  ReserveStock   reserva el stock (todo o nada)         StockReserved | StockReservationFailed
  ReleaseStock   compensa: libera la reserva            StockReleased (siempre, aunque no hubiera nada)

Los comandos pueden llegar REPETIDOS: si el orquestador no recibe respuesta a
tiempo, reenvía el comando (con otro eventId). Por eso, además del inbox, cada
comando es idempotente por pedido: reservar dos veces el mismo pedido no
descuenta dos veces; responde lo que ya hizo.
"""

import logging

from sqlalchemy import Connection, Engine, select, update

from app.db import Tables
from saga_kit.envelope import new_event
from saga_kit.inbox import first_time
from saga_kit.outbox import enqueue

log = logging.getLogger("inventory")
REPLIES = "saga.replies"


class _OutOfStock(Exception):
    pass


class InventoryParticipant:
    def __init__(self, engine: Engine, tables: Tables):
        self.engine, self.t = engine, tables

    def handle(self, command: dict) -> str:
        handlers = {"ReserveStock": self._reserve, "ReleaseStock": self._release}
        handler = handlers.get(command["type"])
        if handler is None:
            return "IGNORED"
        with self.engine.begin() as conn:
            if not first_time(conn, self.t.processed, command["eventId"]):
                return "DUPLICATE"
            return handler(conn, command)

    def _reply(self, conn: Connection, type_: str, order_id: str, payload: dict) -> None:
        enqueue(conn, self.t.outbox, REPLIES, new_event(type_, order_id, payload))

    def _reserve(self, conn: Connection, command: dict) -> str:
        order_id = command["aggregateId"]
        existing = conn.execute(select(self.t.reservations.c.status)
                                .where(self.t.reservations.c.order_id == order_id)).first()
        if existing is not None:
            # Comando reenviado: ya lo resolvimos. Respondemos lo mismo, sin volver a reservar.
            self._reply(conn, "StockReserved", order_id, {"resent": True})
            return "ALREADY_RESERVED"
        try:
            with conn.begin_nested():
                for item in command["payload"]["items"]:
                    current = conn.execute(select(self.t.stock.c.available)
                                           .where(self.t.stock.c.sku == item["sku"]).with_for_update()).scalar()
                    if current is None or current < item["quantity"]:
                        raise _OutOfStock(f"Stock insuficiente para {item['sku']}: hay {current or 0}")
                    conn.execute(update(self.t.stock).where(self.t.stock.c.sku == item["sku"])
                                 .values(available=self.t.stock.c.available - item["quantity"]))
                    conn.execute(self.t.reservations.insert().values(
                        order_id=order_id, sku=item["sku"], quantity=item["quantity"], status="RESERVED"))
        except _OutOfStock as error:
            self._reply(conn, "StockReservationFailed", order_id, {"reason": str(error)})
            return "FAILED"
        self._reply(conn, "StockReserved", order_id, {})
        log.info("Pedido %s: stock reservado", order_id)
        return "RESERVED"

    def _release(self, conn: Connection, command: dict) -> str:
        order_id = command["aggregateId"]
        rows = conn.execute(select(self.t.reservations)
                            .where(self.t.reservations.c.order_id == order_id,
                                   self.t.reservations.c.status == "RESERVED").with_for_update()).all()
        for row in rows:
            conn.execute(update(self.t.stock).where(self.t.stock.c.sku == row.sku)
                         .values(available=self.t.stock.c.available + row.quantity))
            conn.execute(update(self.t.reservations).where(self.t.reservations.c.id == row.id)
                         .values(status="RELEASED"))
        self._reply(conn, "StockReleased", order_id, {"released": bool(rows)})
        log.info("↩️  Pedido %s: liberación pedida por el orquestador (%s)",
                 order_id, "stock devuelto" if rows else "no había nada reservado")
        return "RELEASED"
