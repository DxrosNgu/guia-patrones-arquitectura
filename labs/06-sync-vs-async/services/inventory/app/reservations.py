"""Reservar stock: lo usan tanto la API REST (modo sync) como el consumidor de Kafka (modo async)."""

from dataclasses import dataclass

from sqlalchemy import Engine, Table, select, update


@dataclass
class Result:
    status: str               # RESERVED | REJECTED
    reason: str | None = None
    unknown_sku: bool = False


class _Rejected(Exception):
    def __init__(self, reason: str, unknown_sku: bool = False):
        super().__init__(reason)
        self.reason, self.unknown_sku = reason, unknown_sku


def reserve(engine: Engine, tables: tuple[Table, Table], order_id: str, items: list[dict],
            processed_by: str) -> Result:
    """Todo o nada: si falta stock de un ítem, no se descuenta ninguno.

    El rechazo también queda registrado (status REJECTED) para que se vea qué pasó.
    ⚠️ Si el mismo evento llega dos veces, se reserva dos veces: lo resuelve el lab 12.
    """
    stock, reservations = tables
    try:
        with engine.begin() as conn:
            for item in items:
                current = conn.execute(
                    select(stock.c.available).where(stock.c.sku == item["sku"]).with_for_update()).scalar()
                if current is None:
                    raise _Rejected(f"No existe el SKU {item['sku']}", unknown_sku=True)
                if current < item["quantity"]:
                    raise _Rejected(f"Stock insuficiente para {item['sku']}: hay {current}")
                conn.execute(update(stock).where(stock.c.sku == item["sku"])
                             .values(available=stock.c.available - item["quantity"]))
                conn.execute(reservations.insert().values(
                    order_id=order_id, sku=item["sku"], quantity=item["quantity"],
                    status="RESERVED", processed_by=processed_by))
        return Result("RESERVED")
    except _Rejected as rejected:
        with engine.begin() as conn:
            for item in items:
                conn.execute(reservations.insert().values(
                    order_id=order_id, sku=item["sku"], quantity=item["quantity"],
                    status="REJECTED", reason=rejected.reason, processed_by=processed_by))
        return Result("REJECTED", rejected.reason, rejected.unknown_sku)
