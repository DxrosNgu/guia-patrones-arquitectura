from app.worker import follow_up_events


def test_an_order_placed_produces_a_payment_and_then_a_shipment():
    order = {"eventId": "e-1", "type": "OrderPlaced", "aggregateId": "o-1", "version": 1,
             "occurredAt": "2026-11-17T10:00:00Z", "payload": {"total": 99.8}}

    payment, shipment = follow_up_events(order)

    assert (payment.topic, payment.event["type"], payment.event["aggregateId"]) == ("payments.events", "PaymentConfirmed", "o-1")
    assert payment.event["payload"]["amount"] == 99.8
    assert (shipment.topic, shipment.event["type"]) == ("shipping.events", "ShipmentDispatched")
    assert shipment.event["payload"]["trackingCode"].startswith("RS-")
    assert payment.event["eventId"] != shipment.event["eventId"]


def test_other_events_produce_nothing():
    assert follow_up_events({"type": "OrderCancelled", "aggregateId": "o-1", "payload": {}}) == []
