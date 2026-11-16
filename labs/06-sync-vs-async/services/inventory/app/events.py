"""Procesamiento de los eventos de pedidos (topic orders.events)."""

import json
import logging
import socket
from collections.abc import Callable
from dataclasses import dataclass

from sqlalchemy import Engine, Table

from app.reservations import reserve

log = logging.getLogger("inventory")


class InvalidEvent(Exception):
    """El mensaje no se puede procesar NUNCA (mal formado). Va a la DLQ."""


class TransientError(Exception):
    """Falla que puede resolverse sola (otro servicio caído). Se reintenta."""


@dataclass
class Event:
    event_id: str
    type: str
    aggregate_id: str
    payload: dict


def parse_event(raw: bytes) -> Event:
    try:
        data = json.loads(raw)
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise InvalidEvent(f"No es JSON válido: {error}") from error
    if not isinstance(data, dict):
        raise InvalidEvent("El evento debe ser un objeto JSON")
    missing = [field for field in ("eventId", "type", "aggregateId", "payload") if field not in data]
    if missing:
        raise InvalidEvent(f"Faltan campos del envelope: {', '.join(missing)}")
    return Event(data["eventId"], data["type"], data["aggregateId"], data["payload"])


class OrderEventHandler:
    def __init__(self, engine: Engine, tables: tuple[Table, Table],
                 fetch_items: Callable[[str], list[dict]] | None = None,
                 consumer_name: str | None = None):
        self.engine, self.tables = engine, tables
        self.fetch_items = fetch_items
        self.consumer_name = consumer_name or socket.gethostname()

    def handle(self, raw: bytes) -> str:
        event = parse_event(raw)
        if event.type != "OrderPlaced":
            return "IGNORED"

        items = event.payload.get("items")
        if items is None:
            # Event Notification: el evento no trae los datos → hay que pedírselos a pedidos.
            # Si pedidos está caído, este consumidor TAMBIÉN se detiene (acoplamiento de vuelta).
            if self.fetch_items is None:
                raise InvalidEvent("El evento no trae ítems y no hay cómo pedirlos")
            items = self.fetch_items(event.aggregate_id)

        result = reserve(self.engine, self.tables, event.aggregate_id, items, self.consumer_name)
        log.info("[%s] Pedido %s → %s %s", self.consumer_name, event.aggregate_id, result.status,
                 result.reason or "")
        return result.status
