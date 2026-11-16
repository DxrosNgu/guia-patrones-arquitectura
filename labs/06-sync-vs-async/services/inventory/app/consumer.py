"""Consumidor de Kafka.

Reglas para cada mensaje:
  - Se procesa bien            → se confirma el offset y se sigue con el siguiente.
  - Está mal formado (veneno)  → va a la Dead Letter Queue y se sigue. Reintentarlo
                                  no sirve: fallaría siempre y bloquearía la partición.
  - Falla transitoria          → se reintenta (con espera) hasta que funcione.
                                  Mientras tanto, esa partición queda detenida.
"""

import asyncio
import logging

import httpx
from aiokafka import AIOKafkaConsumer, AIOKafkaProducer

from app.events import InvalidEvent, TransientError

log = logging.getLogger("inventory")


async def process_message(raw: bytes, handler, dlq, backoff_seconds: float) -> str:
    attempt = 0
    while True:
        attempt += 1
        try:
            # El handler usa la base de forma síncrona: lo corremos en otro hilo.
            return await asyncio.to_thread(handler.handle, raw)
        except InvalidEvent as error:
            log.warning("Mensaje inválido → DLQ: %s", error)
            await dlq.send(raw, str(error))
            return "DLQ"
        except TransientError as error:
            log.warning("Falla transitoria (intento %d), reintento en %.1fs: %s",
                        attempt, backoff_seconds, error)
            await asyncio.sleep(backoff_seconds)


class KafkaDlq:
    def __init__(self, producer: AIOKafkaProducer, topic: str):
        self.producer, self.topic = producer, topic

    async def send(self, raw: bytes, reason: str):
        await self.producer.send_and_wait(self.topic, raw, headers=[("dlq-reason", reason.encode())])


def orders_items_fetcher(orders_url: str):
    client = httpx.Client(timeout=3.0)

    def fetch(order_id: str) -> list[dict]:
        try:
            order = client.get(f"{orders_url}/api/orders/{order_id}").raise_for_status().json()
        except httpx.HTTPError as error:
            raise TransientError(f"No pude pedirle el pedido {order_id} a orders: {error}") from error
        return order["items"]

    return fetch


async def run_consumer(settings, handler):
    consumer = AIOKafkaConsumer(
        settings.kafka_topic,
        bootstrap_servers=settings.kafka_bootstrap_servers,
        group_id=settings.kafka_group_id,
        enable_auto_commit=False,          # confirmamos a mano, DESPUÉS de procesar
        auto_offset_reset="earliest",
    )
    producer = AIOKafkaProducer(bootstrap_servers=settings.kafka_bootstrap_servers)
    await consumer.start()
    await producer.start()
    dlq = KafkaDlq(producer, settings.kafka_dlq_topic)
    log.info("Consumidor '%s' escuchando %s", settings.kafka_group_id, settings.kafka_topic)
    try:
        async for message in consumer:
            await process_message(message.value, handler, dlq, settings.retry_backoff_seconds)
            await consumer.commit()
    finally:
        await consumer.stop()
        await producer.stop()
