from sqlalchemy import Connection, Table
from sqlalchemy.dialects.postgresql import insert


def first_time(conn: Connection, processed: Table, event_id: str) -> bool:
    """Idempotent Consumer: True la primera vez que se ve este eventId (dentro de la transacción)."""
    row = conn.execute(
        insert(processed).values(event_id=event_id)
        .on_conflict_do_nothing(index_elements=["event_id"])
        .returning(processed.c.event_id)
    ).first()
    return row is not None
