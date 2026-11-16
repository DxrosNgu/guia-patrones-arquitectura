"""Consumidor de Kafka con entrega "al menos una vez".

El offset se confirma DESPUÉS de procesar. Si el proceso muere entre ambas
cosas, Kafka vuelve a entregar el mensaje al reiniciar: por eso el consumidor
tiene que ser idempotente.
"""

import asyncio
import logging
import os
from pathlib import Path

from aiokafka import AIOKafkaConsumer, AIOKafkaProducer

from app.events import InvalidEvent, TransientError

log = logging.getLogger("inventory")


async def process_message(raw: bytes, handler, dlq, backoff_seconds: float) -> str:
    attempt = 0
    while True:
        attempt += 1
        try:
            return await asyncio.to_thread(handler.handle, raw)
        except InvalidEvent as error:
            log.warning("Mensaje inválido → DLQ: %s", error)
            await dlq.send(raw, str(error))
            return "DLQ"
        except TransientError as error:
            log.warning("Falla transitoria (intento %d), reintento en %.1fs: %s", attempt, backoff_seconds, error)
            await asyncio.sleep(backoff_seconds)


class KafkaDlq:
    def __init__(self, producer: AIOKafkaProducer, topic: str):
        self.producer, self.topic = producer, topic

    async def send(self, raw: bytes, reason: str):
        await self.producer.send_and_wait(self.topic, raw, headers=[("dlq-reason", reason.encode())])


class CrashOnce:
    """Caos controlado: matar el proceso UNA vez, justo antes de confirmar el offset.

    El archivo `marker` sobrevive al reinicio del contenedor, así que la segunda
    vez no se vuelve a caer (si no, entraría en un bucle infinito).
    """

    def __init__(self, enabled: bool, marker: Path = Path("/tmp/chaos-crashed"), exit=os._exit):
        self.enabled, self.marker, self.exit = enabled, marker, exit

    def maybe_crash(self):
        if self.enabled and not self.marker.exists():
            self.marker.touch()
            log.error("💥 CAOS: el proceso muere ANTES de confirmar el offset. Kafka lo reentregará.")
            self.exit(1)


async def run_consumer(settings, handler):
    consumer = AIOKafkaConsumer(
        settings.kafka_topic,
        bootstrap_servers=settings.kafka_bootstrap_servers,
        group_id=settings.kafka_group_id,
        enable_auto_commit=False,
        auto_offset_reset="earliest",
    )
    producer = AIOKafkaProducer(bootstrap_servers=settings.kafka_bootstrap_servers)
    await consumer.start()
    await producer.start()
    dlq = KafkaDlq(producer, settings.kafka_dlq_topic)
    chaos = CrashOnce(settings.chaos_crash_before_commit)
    try:
        async for message in consumer:
            await process_message(message.value, handler, dlq, settings.retry_backoff_seconds)
            chaos.maybe_crash()            # ← entre "procesado" y "confirmado"
            await consumer.commit()
    finally:
        await consumer.stop()
        await producer.stop()
