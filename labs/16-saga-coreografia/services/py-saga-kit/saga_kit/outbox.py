import asyncio
import json
import logging
from collections.abc import Callable
from datetime import datetime, timezone

from sqlalchemy import Connection, Engine, Table, select, update

log = logging.getLogger("saga")


def enqueue(conn: Connection, outbox: Table, topic: str, event: dict) -> None:
    """Guarda el evento en la outbox, DENTRO de la transacción del cambio de negocio."""
    conn.execute(outbox.insert().values(id=event["eventId"], topic=topic, key=event["aggregateId"],
                                        payload=json.dumps(event)))


def publish_pending(engine: Engine, outbox: Table, send: Callable[[str, str, str], None], limit: int = 50) -> int:
    """Polling Publisher: publica lo pendiente y lo marca. Si send() falla, todo queda pendiente."""
    with engine.begin() as conn:
        rows = conn.execute(
            select(outbox.c.id, outbox.c.topic, outbox.c.key, outbox.c.payload)
            .where(outbox.c.published_at.is_(None))
            .order_by(outbox.c.created_at)
            .limit(limit)
            .with_for_update(skip_locked=True)
        ).all()
        for row in rows:
            send(row.topic, row.key, row.payload)
            conn.execute(update(outbox).where(outbox.c.id == row.id)
                         .values(published_at=datetime.now(timezone.utc)))
    return len(rows)


async def relay_forever(engine: Engine, outbox: Table, producer, interval: float = 0.5) -> None:
    loop = asyncio.get_running_loop()

    def send(topic: str, key: str, value: str) -> None:
        # publish_pending corre en otro hilo; el productor de aiokafka vive en el loop principal.
        future = asyncio.run_coroutine_threadsafe(
            producer.send_and_wait(topic, value.encode(), key=key.encode()), loop)
        future.result(timeout=10)

    while True:
        try:
            sent = await asyncio.to_thread(publish_pending, engine, outbox, send)
            if sent:
                log.info("📤 Outbox: %d evento(s) publicados", sent)
        except Exception as error:  # noqa: BLE001 — se reintenta en la próxima vuelta
            log.warning("Outbox: no se pudo publicar (%s); reintento en %.1fs", error, interval)
        await asyncio.sleep(interval)
