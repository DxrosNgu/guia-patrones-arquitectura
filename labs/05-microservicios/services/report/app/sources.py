"""Dos formas de armar el MISMO reporte: "pedidos con el stock actual de cada invento".

ApiSource       pregunta a cada servicio por su API pública (API Composition).
DatabaseSource  hace un JOIN sobre tablas que pertenecen a OTROS equipos.

Las dos devuelven lo mismo... hasta que el equipo de inventario cambia algo
interno. Ver "Rómpelo" en el README del lab.
"""

import httpx
from sqlalchemy import Engine, text
from sqlalchemy.exc import SQLAlchemyError


class SourceError(Exception):
    """El reporte no se pudo armar."""


def compose_report(orders: list[dict], stock: dict[str, int]) -> list[dict]:
    return [
        {
            "orderId": order["id"],
            "status": order["status"],
            "total": order["total"],
            "currency": order["currency"],
            "lines": [
                {"sku": line["sku"], "quantity": line["quantity"], "stockAvailable": stock.get(line["sku"])}
                for line in order["lines"]
            ],
        }
        for order in orders
    ]


class ApiSource:
    name = "api"

    def __init__(self, orders_url: str, inventory_url: str, client: httpx.Client | None = None):
        self.orders_url = orders_url
        self.inventory_url = inventory_url
        self.client = client or httpx.Client(timeout=5.0)

    def fetch(self) -> list[dict]:
        try:
            orders = self.client.get(f"{self.orders_url}/api/orders").raise_for_status().json()
            stock = self.client.get(f"{self.inventory_url}/api/stock").raise_for_status().json()
        except httpx.HTTPError as error:
            raise SourceError(f"Un servicio no respondió: {error}") from error
        return compose_report(orders, {item["sku"]: item["available"] for item in stock})


# ❌ Este SQL conoce los nombres de tablas y columnas de pedidos E inventario.
#    Cualquier cambio interno de esos equipos lo rompe.
REPORT_SQL = text("""
    SELECT o.id, o.status, o.total, o.currency, l.sku, l.quantity, s.available
    FROM orders o
    JOIN order_lines l ON l.order_id = o.id
    LEFT JOIN stock s ON s.sku = l.sku
    ORDER BY o.created_at DESC, l.sku
""")


class DatabaseSource:
    name = "database"

    def __init__(self, engine: Engine):
        self.engine = engine

    def fetch(self) -> list[dict]:
        try:
            with self.engine.connect() as conn:
                rows = conn.execute(REPORT_SQL).all()
        except SQLAlchemyError as error:
            raise SourceError(str(error.orig or error).splitlines()[0]) from error

        orders: dict[str, dict] = {}
        for order_id, status, total, currency, sku, quantity, available in rows:
            order = orders.setdefault(str(order_id), {
                "orderId": str(order_id), "status": status, "total": float(total),
                "currency": currency, "lines": [],
            })
            order["lines"].append({"sku": sku, "quantity": quantity, "stockAvailable": available})
        return list(orders.values())
