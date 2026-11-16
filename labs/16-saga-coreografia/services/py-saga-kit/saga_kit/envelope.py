import json
import uuid
from datetime import datetime, timezone

FIELDS = ("eventId", "type", "aggregateId", "occurredAt", "payload")


class InvalidEvent(Exception):
    """El mensaje no se puede procesar nunca: va a la DLQ."""


class TransientError(Exception):
    """Falla pasajera (un tercero caído): se reintenta el mismo mensaje."""


def new_event(type_: str, order_id: str, payload: dict) -> dict:
    return {
        "eventId": str(uuid.uuid4()),
        "type": type_,
        "aggregateId": order_id,
        "occurredAt": datetime.now(timezone.utc).isoformat(),
        "payload": payload,
    }


def parse_event(raw: bytes) -> dict:
    try:
        data = json.loads(raw)
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise InvalidEvent(f"No es JSON válido: {error}") from error
    if not isinstance(data, dict) or any(field not in data for field in FIELDS):
        raise InvalidEvent("No respeta el envelope (eventId, type, aggregateId, occurredAt, payload)")
    return data
