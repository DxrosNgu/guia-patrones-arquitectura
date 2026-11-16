from sqlalchemy import create_engine, inspect


def reserve(client, order_id, *items):
    return client.post(
        "/api/reservations",
        json={"orderId": order_id, "items": [{"sku": sku, "quantity": qty} for sku, qty in items]},
    )


def available(client, sku):
    return client.get(f"/api/stock/{sku}").json()["available"]


def test_lists_seeded_stock(client):
    stock = {item["sku"]: item["available"] for item in client.get("/api/stock").json()}

    assert stock == {"INV-001": 12, "INV-002": 0, "INV-003": 5, "INV-004": 30}


def test_reservation_decrements_stock(client):
    response = reserve(client, "order-1", ("INV-001", 2), ("INV-003", 1))

    assert response.status_code == 201
    assert response.json() == {"orderId": "order-1", "status": "RESERVED"}
    assert available(client, "INV-001") == 10
    assert available(client, "INV-003") == 4


def test_insufficient_stock_reserves_nothing(client):
    response = reserve(client, "order-2", ("INV-001", 1), ("INV-002", 1))

    assert response.status_code == 409
    assert "INV-002" in response.json()["detail"]
    # Todo o nada: el INV-001 tampoco se descontó.
    assert available(client, "INV-001") == 12


def test_unknown_sku_is_not_found(client):
    response = reserve(client, "order-3", ("NOPE", 1))

    assert response.status_code == 404


def test_seed_is_idempotent(make_client):
    make_client()
    second = make_client()

    assert len(second.get("/api/stock").json()) == 4


def test_v2_renames_the_column_but_keeps_the_api_contract(make_client):
    make_client()  # v1 crea la columna "available"
    client_v2 = make_client(inventory_v2=True)

    engine = create_engine(client_v2.app.state.settings.database_url)
    columns = {c["name"] for c in inspect(engine).get_columns("stock")}
    engine.dispose()

    assert "units_available" in columns and "available" not in columns
    # El contrato de la API NO cambia: sigue diciendo "available".
    assert client_v2.get("/api/stock/INV-004").json() == {"sku": "INV-004", "available": 30}


def test_health(client):
    assert client.get("/health").json() == {"status": "UP"}
