"""El BFF compone "Mis pedidos" con datos de 3 servicios. Los servicios se simulan con httpx.MockTransport."""
import asyncio
import json
import time

import httpx
import pytest

from app.compose import OrdersUnavailable, my_orders
from app.services import Services

ORDERS = [
    {"id": "ord-1", "customerId": "ana", "createdAt": "2026-11-02T10:15:00Z", "total": 99.8, "status": "PLACED",
     "items": [{"sku": "INV-001", "name": "Paraguas solar", "quantity": 2, "unitPrice": 49.9}]},
    {"id": "ord-2", "customerId": "ana", "createdAt": "2026-11-16T09:05:00Z", "total": 76.5, "status": "PLACED",
     "items": [{"sku": "INV-004", "name": "Taza", "quantity": 3, "unitPrice": 25.5}]},
]
PAYMENTS = [{"orderId": "ord-1", "status": "PAID"}, {"orderId": "ord-2", "status": "PENDING"}]
SHIPMENTS = [{"orderId": "ord-1", "status": "IN_TRANSIT", "trackingCode": "RS-1", "eta": "2026-11-19"}]


def fake_services(fail: set[str] = frozenset(), delay: float = 0.0) -> Services:
    data = {"/api/orders": ORDERS, "/api/payments": PAYMENTS, "/api/shipments": SHIPMENTS}

    async def handler(request: httpx.Request) -> httpx.Response:
        service = request.url.host
        await asyncio.sleep(delay)
        if service in fail:
            raise httpx.ConnectError("caído", request=request)
        return httpx.Response(200, content=json.dumps(data[request.url.path]))

    client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    return Services(client, orders_url="http://orders", payments_url="http://payments",
                    shipping_url="http://shipping", timeout=1.0)


def test_composes_the_screen_from_three_services():
    view = asyncio.run(my_orders(fake_services(), "ana", "parallel"))

    assert view["unavailable"] == []
    first, second = view["orders"]
    assert first == {"id": "ord-1", "date": "2026-11-02", "total": 99.8, "summary": "2 × Paraguas solar",
                     "payment": "Pagado", "shipment": "En camino", "tracking": "RS-1", "eta": "2026-11-19"}
    assert (second["payment"], second["shipment"]) == ("Pago pendiente", "Aún no se envía")


def test_a_missing_service_gives_a_partial_answer_instead_of_an_error():
    view = asyncio.run(my_orders(fake_services(fail={"shipping"}), "ana", "parallel"))

    assert view["unavailable"] == ["shipping"]
    assert [o["shipment"] for o in view["orders"]] == ["Estado de envío no disponible"] * 2
    assert view["orders"][0]["payment"] == "Pagado"   # lo demás sigue llegando


def test_without_orders_there_is_nothing_to_compose():
    with pytest.raises(OrdersUnavailable):
        asyncio.run(my_orders(fake_services(fail={"orders"}), "ana", "parallel"))


def test_parallel_calls_take_the_slowest_not_the_sum():
    start = time.perf_counter()
    asyncio.run(my_orders(fake_services(delay=0.2), "ana", "sequential"))
    sequential = time.perf_counter() - start

    start = time.perf_counter()
    asyncio.run(my_orders(fake_services(delay=0.2), "ana", "parallel"))
    parallel = time.perf_counter() - start

    # orders (0,2) + payments (0,2) + shipping (0,2) ≈ 0,6 s  vs.  orders (0,2) + max(0,2; 0,2) ≈ 0,4 s
    assert sequential >= 0.6
    assert parallel < 0.55


def test_a_slow_service_is_cut_by_the_timeout():
    services = fake_services()
    services.timeout = 0.1

    async def slow(request: httpx.Request) -> httpx.Response:
        if request.url.host == "payments":
            await asyncio.sleep(1)
        data = {"/api/orders": ORDERS, "/api/payments": PAYMENTS, "/api/shipments": SHIPMENTS}
        return httpx.Response(200, content=json.dumps(data[request.url.path]))

    services.client = httpx.AsyncClient(transport=httpx.MockTransport(slow))
    view = asyncio.run(my_orders(services, "ana", "parallel"))

    assert view["unavailable"] == ["payments"]
    assert view["orders"][0]["payment"] == "Estado de pago no disponible"
