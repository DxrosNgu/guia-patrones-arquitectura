"""La PROYECCIÓN: eventos de tres servicios → un documento por pedido, ya armado para la pantalla.

Tres propiedades que la hacen confiable:
- Idempotente: cada evento se aplica una vez (su eventId queda en el documento).
- Tolera el desorden: los eventos llegan por TRES topics distintos, sin orden entre ellos.
  Cada evento escribe SUS campos ($set); el estado se deriva de lo que hay. El resultado
  es el mismo llegue primero el pago o el pedido.
- Se puede reconstruir: borrar la colección y volver a leer los topics da la misma vista.
"""
import re
from datetime import UTC, datetime

from pymongo import ASCENDING, DESCENDING
from pymongo.database import Database
from pymongo.errors import DuplicateKeyError

# El estado que ve el cliente se DERIVA de los datos que llegaron, no de qué evento llegó último.
STATUS_PIPELINE = [{"$set": {"status": {"$switch": {"branches": [
    {"case": {"$ifNull": ["$shipment", False]}, "then": "ENVIADO"},
    {"case": {"$and": [{"$ifNull": ["$payment", False]}, {"$ifNull": ["$customerId", False]}]}, "then": "PAGADO"},
    {"case": {"$ifNull": ["$customerId", False]}, "then": "RECIBIDO"},
], "default": "EN PROCESO"}}}}]


def _fields_for(event: dict) -> dict:
    payload = event["payload"]
    match event["type"]:
        case "OrderPlaced":
            items = [{"sku": i["sku"], "name": i["name"], "quantity": i["quantity"]} for i in payload["items"]]
            return {"$set": {"customerId": payload["customerId"], "createdAt": payload["createdAt"],
                             "items": items, "total": payload["total"],
                             "summary": ", ".join(f"{i['quantity']} × {i['name']}" for i in items),
                             "searchText": " ".join(i["name"] for i in items).lower()},
                    "$max": {"orderVersion": event.get("version", 1)}}
        case "PaymentConfirmed":
            return {"$set": {"payment": {"status": "CONFIRMED", "amount": payload.get("amount"),
                                         "at": event["occurredAt"]}}}
        case "ShipmentDispatched":
            return {"$set": {"shipment": {"status": "DISPATCHED", "carrier": payload.get("carrier"),
                                          "trackingCode": payload.get("trackingCode"), "at": event["occurredAt"]}}}
        case _:
            return {}


class Projection:
    def __init__(self, db: Database):
        self.orders = db["orders_view"]
        self.meta = db["projection_status"]
        self.orders.create_index([("customerId", ASCENDING), ("createdAt", DESCENDING)])
        self.orders.create_index("status")

    def apply(self, event: dict) -> bool:
        """Aplica el evento. Devuelve False si ya estaba aplicado (reentrega de Kafka)."""
        update = _fields_for(event)
        if not update:
            return False
        order_id, event_id = event["aggregateId"], event["eventId"]
        update.setdefault("$set", {})["updatedAt"] = datetime.now(UTC).isoformat()
        update["$addToSet"] = {"appliedEvents": event_id}
        update["$inc"] = {"eventsApplied": 1}
        try:
            # El filtro excluye el documento si ya tiene este eventId. Si existe y lo tiene,
            # el upsert intenta INSERTAR otro con el mismo _id → DuplicateKeyError → ya aplicado.
            result = self.orders.update_one({"_id": order_id, "appliedEvents": {"$ne": event_id}}, update, upsert=True)
        except DuplicateKeyError:
            return False
        if result.matched_count == 0 and result.upserted_id is None:
            return False
        self.orders.update_one({"_id": order_id}, STATUS_PIPELINE)
        self.meta.update_one({"_id": "order-history"},
                             {"$set": {"lastEventAt": event["occurredAt"], "lastAppliedAt": datetime.now(UTC).isoformat()},
                              "$inc": {"eventsApplied": 1}}, upsert=True)
        return True

    @staticmethod
    def _view(doc: dict) -> dict:
        doc = dict(doc)
        doc["id"] = doc.pop("_id")
        for internal in ("appliedEvents", "searchText"):
            doc.pop(internal, None)
        return doc

    def get(self, order_id: str) -> dict | None:
        doc = self.orders.find_one({"_id": order_id})
        return self._view(doc) if doc else None

    def search(self, customer_id: str, status: str | None = None, text: str | None = None, limit: int = 50) -> list[dict]:
        query: dict = {"customerId": customer_id}
        if status:
            query["status"] = status
        if text:
            query["searchText"] = {"$regex": re.escape(text.lower())}
        cursor = self.orders.find(query).sort("createdAt", DESCENDING).limit(limit)
        return [self._view(d) for d in cursor]

    def status(self) -> dict:
        meta = self.meta.find_one({"_id": "order-history"}) or {}
        return {"documents": self.orders.count_documents({}), "eventsApplied": meta.get("eventsApplied", 0),
                "lastEventAt": meta.get("lastEventAt"), "lastAppliedAt": meta.get("lastAppliedAt"),
                "projectionVersion": meta.get("projectionVersion")}

    def reset_if_version_changed(self, version: int) -> bool:
        """Si la versión de la proyección cambió, se vacía la vista: el proyector la rearma desde el principio."""
        meta = self.meta.find_one({"_id": "order-history"}) or {}
        if meta.get("projectionVersion") == version:
            return False
        self.orders.delete_many({})
        self.meta.replace_one({"_id": "order-history"}, {"projectionVersion": version, "eventsApplied": 0}, upsert=True)
        return True
