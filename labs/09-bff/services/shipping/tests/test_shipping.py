from fastapi.testclient import TestClient

from app.main import create_app


def test_shipments_of_several_orders_in_one_call():
    client = TestClient(create_app())
    body = client.get("/api/shipments", params={"orderIds": "ord-1001,ord-1003"}).json()

    # ord-1003 todavía no se envió: no aparece.
    assert [s["orderId"] for s in body] == ["ord-1001"]
    assert body[0]["status"] == "DELIVERED"


def test_order_ids_are_required():
    assert TestClient(create_app()).get("/api/shipments").status_code == 400
