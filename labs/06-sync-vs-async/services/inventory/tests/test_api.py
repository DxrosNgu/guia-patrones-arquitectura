def reserve(client, order_id, sku, qty):
    return client.post("/api/reservations",
                       json={"orderId": order_id, "items": [{"sku": sku, "quantity": qty}]})


def test_rest_reservation_still_works_for_the_sync_mode(client):
    response = reserve(client, "o-1", "INV-001", 2)

    assert response.status_code == 201
    assert client.get("/api/stock/INV-001").json()["available"] == 10


def test_rest_reservation_without_stock_is_a_conflict(client):
    assert reserve(client, "o-2", "INV-002", 1).status_code == 409


def test_lists_reservations_with_who_processed_them(client):
    reserve(client, "o-1", "INV-001", 1)

    reservations = client.get("/api/reservations").json()

    assert reservations[0]["orderId"] == "o-1"
    assert reservations[0]["status"] == "RESERVED"
    assert reservations[0]["processedBy"]
