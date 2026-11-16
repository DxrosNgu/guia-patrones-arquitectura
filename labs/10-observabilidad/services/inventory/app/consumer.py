"""Consumidor de Kafka, con la traza continuada desde orders."""

import asyncio
import logging

from aiokafka import AIOKafkaConsumer, AIOKafkaProducer
from opentelemetry.trace import SpanKind

from app.events import InvalidEvent, TransientError
from app.telemetry import context_from_kafka_headers, tracer

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


class ConsumerState:
    """Para el readiness check: ¿el consumidor sigue corriendo?"""

    def __init__(self):
        self.running = False


async def run_consumer(settings, handler, state: ConsumerState):
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
    state.running = True
    try:
        async for message in consumer:
            # 👇 La clave de la traza distribuida: orders puso "traceparent" en los headers
            #    del mensaje. Lo leemos y abrimos un span HIJO de esa traza.
            parent = context_from_kafka_headers(message.headers)
            with tracer.start_as_current_span(f"{message.topic} process", context=parent, kind=SpanKind.CONSUMER,
                                              attributes={"messaging.system": "kafka",
                                                          "messaging.destination.name": message.topic,
                                                          "messaging.kafka.partition": message.partition}):
                await process_message(message.value, handler, dlq, settings.retry_backoff_seconds)
            await consumer.commit()
    finally:
        state.running = False
        await consumer.stop()
        await producer.stop()
