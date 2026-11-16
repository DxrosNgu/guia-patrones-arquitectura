import asyncio
import logging
from collections.abc import Callable

from aiokafka import AIOKafkaConsumer, AIOKafkaProducer

from saga_kit.envelope import InvalidEvent, TransientError, parse_event

log = logging.getLogger("saga")


async def consume_forever(bootstrap: str, group_id: str, topics: list[str], handle: Callable[[dict], None],
                          producer: AIOKafkaProducer, retry_backoff: float = 2.0) -> None:
    """Lee los topics y llama a handle(evento) en un hilo.

    - Evento inválido      → <topic>.dlq y se sigue.
    - TransientError       → se reintenta el MISMO mensaje (la partición espera).
    - El offset se confirma DESPUÉS de procesar (al menos una vez).
    """
    consumer = AIOKafkaConsumer(*topics, bootstrap_servers=bootstrap, group_id=group_id,
                                enable_auto_commit=False, auto_offset_reset="earliest")
    await consumer.start()
    log.info("Escuchando %s como '%s'", ", ".join(topics), group_id)
    try:
        async for message in consumer:
            attempt = 0
            while True:
                attempt += 1
                try:
                    event = parse_event(message.value)
                    await asyncio.to_thread(handle, event)
                    break
                except InvalidEvent as error:
                    log.warning("Mensaje inválido en %s → DLQ: %s", message.topic, error)
                    await producer.send_and_wait(f"{message.topic}.dlq", message.value,
                                                 headers=[("dlq-reason", str(error).encode())])
                    break
                except TransientError as error:
                    log.warning("⏳ Falla transitoria (intento %d), reintento en %.0fs: %s",
                                attempt, retry_backoff, error)
                    await asyncio.sleep(retry_backoff)
            await consumer.commit()
    finally:
        await consumer.stop()
