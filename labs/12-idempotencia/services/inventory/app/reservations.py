"""Reservar stock a partir de un evento, de forma idempotente."""

from dataclasses import dataclass

from sqlalchemy import Engine, Table, select, update
from sqlalchemy.dialects.postgresql import insert


@dataclass
class Result:
    status: str               # RESERVED | REJECTED | DUPLICATE
    reason: str | None = None


class _Rejected(Exception):
    pass


def reserve(engine: Engine, tables: tuple[Table, Table, Table], event_id: str, order_id: str,
            items: list[dict], processed_by: str, idempotent: bool = True) -> Result:
    stock, reservations, processed = tables

    # UNA transacción para todo: "recordar el evento" + "aplicar su efecto".
    # Si cualquiera de las dos falla, ninguna queda hecha. Por eso funciona.
    with engine.begin() as conn:
        if idempotent:
            # Si otro hilo/réplica está procesando el mismo eventId, este INSERT ESPERA a que
            # termine. Si la otra transacción confirmó, no se inserta nada → es un duplicado.
            remembered = conn.execute(
                insert(processed).values(event_id=event_id, order_id=order_id, processed_by=processed_by)
                .on_conflict_do_nothing(index_elements=["event_id"])
                .returning(processed.c.event_id)
            ).first()
            if remembered is None:
                return Result("DUPLICATE")

        try:
            with conn.begin_nested():   # SAVEPOINT: si falta stock, se deshace solo esta parte
                for item in items:
                    current = conn.execute(select(stock.c.available)
                                           .where(stock.c.sku == item["sku"]).with_for_update()).scalar()
                    if current is None or current < item["quantity"]:
                        raise _Rejected(f"Stock insuficiente para {item['sku']}: hay {current or 0}")
                    conn.execute(update(stock).where(stock.c.sku == item["sku"])
                                 .values(available=stock.c.available - item["quantity"]))
            result = Result("RESERVED")
        except _Rejected as rejected:
            result = Result("REJECTED", str(rejected))

        for item in items:
            conn.execute(reservations.insert().values(
                order_id=order_id, sku=item["sku"], quantity=item["quantity"],
                status=result.status, reason=result.reason, processed_by=processed_by))
        if idempotent:
            conn.execute(update(processed).where(processed.c.event_id == event_id).values(outcome=result.status))
    return result
