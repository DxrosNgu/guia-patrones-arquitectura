"""LA TIENDA LEGADA: un monolito que atiende el catálogo y los pedidos con una sola base.

El contrato de su API (/api/catalog/..., /api/orders/...) es lo que los clientes conocen. Los servicios
nuevos tienen que respetarlo al pie de la letra: el cliente no debe notar quién le responde.

/internal/orders es lo ÚNICO que se le agregó para la migración: exportar sus pedidos e importar los
del servicio nuevo. Estrangular un sistema casi siempre exige tocarlo un poco.
"""
from decimal import Decimal

from fastapi import FastAPI, Response
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from sqlalchemy import create_engine, text

from app.config import Settings
from app.db import init_schema


class PlaceOrder(BaseModel):
    customer: str
    sku: str
    quantity: int = Field(gt=0)


class SyncedOrder(BaseModel):
    customer: str
    sku: str
    quantity: int
    total: Decimal
    createdAt: str


ORDER_COLUMNS = "id, cliente, sku, cantidad, total, creado"


def order_json(row) -> dict:
    return {"id": row.id, "customer": row.cliente, "sku": row.sku, "quantity": row.cantidad,
            "total": float(row.total), "createdAt": row.creado.isoformat()}


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or Settings()
    engine = create_engine(settings.database_url, pool_pre_ping=True)
    init_schema(engine)
    app = FastAPI(title="Tienda legada (monolito)")

    @app.middleware("http")
    async def served_by(request, call_next):
        response = await call_next(request)
        response.headers["X-Served-By"] = "legacy-shop"
        return response

    # ── El contrato público ──────────────────────────────────────────────

    @app.get("/api/catalog/products")
    def products():
        with engine.connect() as conn:
            rows = conn.execute(text("SELECT sku, nombre, precio FROM productos ORDER BY sku")).all()
        return [{"sku": r.sku, "name": r.nombre, "price": float(r.precio)} for r in rows]

    @app.get("/api/catalog/products/{sku}")
    def product(sku: str):
        with engine.connect() as conn:
            r = conn.execute(text("SELECT sku, nombre, precio FROM productos WHERE sku = :s"), {"s": sku}).first()
        if r is None:
            return JSONResponse({"message": f"No existe {sku}"}, status_code=404)
        return {"sku": r.sku, "name": r.nombre, "price": float(r.precio)}

    @app.post("/api/orders", status_code=201)
    def place(order: PlaceOrder):
        with engine.begin() as conn:
            row = conn.execute(text(
                f"INSERT INTO pedidos (cliente, sku, cantidad, total) SELECT :c, sku, :q, precio * :q "
                f"FROM productos WHERE sku = :s RETURNING {ORDER_COLUMNS}"),
                {"c": order.customer, "s": order.sku, "q": order.quantity}).first()
        if row is None:
            return JSONResponse({"message": f"No existe {order.sku}"}, status_code=422)
        return order_json(row)

    @app.get("/api/orders/{order_id}")
    def get_order(order_id: int):
        with engine.connect() as conn:
            row = conn.execute(text(f"SELECT {ORDER_COLUMNS} FROM pedidos WHERE id = :id"), {"id": order_id}).first()
        return order_json(row) if row else JSONResponse({"message": "No existe"}, status_code=404)

    @app.get("/api/orders")
    def list_orders(customer: str):
        with engine.connect() as conn:
            rows = conn.execute(text(f"SELECT {ORDER_COLUMNS} FROM pedidos WHERE cliente = :c ORDER BY id DESC"),
                                {"c": customer}).all()
        return [order_json(r) for r in rows]

    # ── Agregado para la migración ───────────────────────────────────────

    @app.get("/internal/orders")
    def export_orders(afterId: int = 0, limit: int = 100):
        """Los pedidos CREADOS en el legado (no los que le sincronizó el nuevo), en orden de id."""
        with engine.connect() as conn:
            rows = conn.execute(text(f"SELECT {ORDER_COLUMNS} FROM pedidos WHERE origen = 'legado' AND id > :a "
                                     "ORDER BY id LIMIT :l"), {"a": afterId, "l": min(limit, 500)}).all()
        return [order_json(r) for r in rows]

    @app.put("/internal/orders/{order_id}", status_code=204)
    def import_order(order_id: int, order: SyncedOrder):
        """Un pedido creado en el servicio NUEVO. Idempotente: repetirlo no duplica nada."""
        with engine.begin() as conn:
            conn.execute(text("INSERT INTO pedidos (id, cliente, sku, cantidad, total, creado, origen) "
                              "VALUES (:id, :c, :s, :q, :t, CAST(:at AS timestamptz), 'nuevo') ON CONFLICT (id) DO NOTHING"),
                         {"id": order_id, "c": order.customer, "s": order.sku, "q": order.quantity,
                          "t": order.total, "at": order.createdAt})
        return Response(status_code=204)

    @app.get("/health")
    def health():
        with engine.connect() as conn:
            conn.execute(text("SELECT 1"))
        return {"status": "UP"}

    return app
