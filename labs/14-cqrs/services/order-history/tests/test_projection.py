"""La proyección: convierte eventos de 3 servicios en UN documento por pedido, listo para la pantalla."""
from app.projection import Projection


def placed(order_id="o-1", event_id="e-1", version=1, customer="ana"):
    return {"eventId": event_id, "type": "OrderPlaced", "aggregateId": order_id, "version": version,
            "occurredAt": "2026-11-17T10:00:00Z",
            "payload": {"customerId": customer, "createdAt": "2026-11-17T10:00:00Z", "total": 99.8,
                        "items": [{"sku": "INV-001", "name": "Paraguas solar", "quantity": 2, "unitPrice": 49.9}]}}


def paid(order_id="o-1", event_id="p-1"):
    return {"eventId": event_id, "type": "PaymentConfirmed", "aggregateId": order_id,
            "occurredAt": "2026-11-17T10:00:02Z", "payload": {"amount": 99.8}}


def shipped(order_id="o-1", event_id="s-1"):
    return {"eventId": event_id, "type": "ShipmentDispatched", "aggregateId": order_id,
            "occurredAt": "2026-11-17T10:00:05Z", "payload": {"carrier": "Rápido SAC", "trackingCode": "RS-1"}}


def test_an_order_placed_becomes_a_document_ready_to_show(db):
    projection = Projection(db)

    projection.apply(placed())

    doc = projection.get("o-1")
    assert doc["customerId"] == "ana"
    assert doc["status"] == "RECIBIDO"
    assert doc["summary"] == "2 × Paraguas solar"
    assert doc["orderVersion"] == 1


def test_payment_and_shipment_from_other_services_enrich_the_same_document(db):
    projection = Projection(db)
    for event in (placed(), paid(), shipped()):
        projection.apply(event)

    doc = projection.get("o-1")
    assert doc["status"] == "ENVIADO"
    assert doc["payment"]["status"] == "CONFIRMED"
    assert doc["shipment"]["trackingCode"] == "RS-1"


def test_a_repeated_event_is_applied_once(db):
    projection = Projection(db)

    assert projection.apply(placed()) is True
    assert projection.apply(placed()) is False   # Kafka lo reentregó: se ignora

    assert projection.get("o-1")["eventsApplied"] == 1


def test_events_from_different_topics_can_arrive_out_of_order(db):
    projection = Projection(db)

    projection.apply(paid())                       # el pago llega ANTES que el pedido (otro topic)
    assert projection.get("o-1")["status"] == "EN PROCESO"
    projection.apply(placed())

    doc = projection.get("o-1")
    assert doc["status"] == "PAGADO"
    assert doc["customerId"] == "ana"
    assert doc["payment"]["status"] == "CONFIRMED"


def test_search_by_customer_status_and_text(db):
    projection = Projection(db)
    projection.apply(placed("o-1", "e-1"))
    projection.apply(paid("o-1", "p-1"))
    projection.apply(placed("o-2", "e-2"))
    projection.apply(placed("o-3", "e-3", customer="beto"))

    assert [d["id"] for d in projection.search(customer_id="ana", status="PAGADO")] == ["o-1"]
    assert {d["id"] for d in projection.search(customer_id="ana", text="paraguas")} == {"o-1", "o-2"}
    assert projection.search(customer_id="ana", text="lámpara") == []


def test_status_reports_how_far_behind_the_view_is(db):
    projection = Projection(db)
    projection.apply(placed())

    status = projection.status()
    assert status["documents"] == 1
    assert status["eventsApplied"] == 1
    assert status["lastEventAt"] == "2026-11-17T10:00:00Z"
