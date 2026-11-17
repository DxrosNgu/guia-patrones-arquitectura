"""SIMULADOR de pagos y envíos: en este lab no importa CÓMO se paga ni se envía,
solo que esos servicios publican eventos que el historial tiene que juntar.

Por cada OrderPlaced: a los PAYMENT_DELAY_S publica PaymentConfirmed (payments.events)
y a los SHIPPING_DELAY_S publica ShipmentDispatched (shipping.events).

    python -m app.worker
"""
import asyncio
import json
import logging
import os
import random
import signal
import uuid
from dataclasses import dataclass
from datetime import UTC, datetime

from aiokafka import AIOKafkaConsumer, AIOKafkaProducer

log = logging.getLogger("fulfillment")


@dataclass
class Outgoing:
    topic: str
    event: dict
    delay_s: float


def _event(event_type: str, order_id: str, payload: dict) -> dict:
    return {"eventId": str(uuid.uuid4()), "type": event_type, "aggregateId": order_id,
            "occurredAt": datetime.now(UTC).isoformat(), "payload": payload}


def follow_up_events(order_event: dict, payment_delay: float = 2.0, shipping_delay: float = 5.0) -> list[Outgoing]:
    if order_event.get("type") != "OrderPlaced":
        return []
    order_id = order_event["aggregateId"]
    return [
        Outgoing("payments.events", _event("PaymentConfirmed", order_id,
                                           {"amount": order_event["payload"].get("total")}), payment_delay),
        Outgoing("shipping.events", _event("ShipmentDispatched", order_id,
                                           {"carrier": "Rápido SAC", "trackingCode": f"RS-{random.randint(10000, 99999)}"}),
                 shipping_delay),
    ]


async def _publish_later(producer: AIOKafkaProducer, outgoing: Outgoing) -> None:
    await asyncio.sleep(outgoing.delay_s)
    outgoing.event["occurredAt"] = datetime.now(UTC).isoformat()
    await producer.send_and_wait(outgoing.topic, json.dumps(outgoing.event).encode(),
                                 key=outgoing.event["aggregateId"].encode())
    log.info("%s → %s (%s)", outgoing.event["type"], outgoing.topic, outgoing.event["aggregateId"])


async def run() -> None:
    servers = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    payment_delay = float(os.getenv("PAYMENT_DELAY_S", "2"))
    shipping_delay = float(os.getenv("SHIPPING_DELAY_S", "5"))
    consumer = AIOKafkaConsumer("orders.events", bootstrap_servers=servers, group_id="fulfillment",
                                auto_offset_reset="earliest")
    producer = AIOKafkaProducer(bootstrap_servers=servers, acks="all")
    await consumer.start()
    await producer.start()
    pending: set[asyncio.Task] = set()
    try:
        async for message in consumer:
            for outgoing in follow_up_events(json.loads(message.value), payment_delay, shipping_delay):
                task = asyncio.create_task(_publish_later(producer, outgoing))
                pending.add(task)
                task.add_done_callback(pending.discard)
    finally:
        await consumer.stop()
        await producer.stop()


async def main() -> None:
    # SIGTERM (docker stop) → cancelar → el `finally` cierra consumidor y productor. Ver app/projector.py
    # del historial: sin handler, Python como PID 1 ignora SIGTERM.
    asyncio.get_running_loop().add_signal_handler(signal.SIGTERM, asyncio.current_task().cancel)
    try:
        await run()
    except asyncio.CancelledError:
        log.info("SIGTERM: detenido")


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)-5s [%(name)s] %(message)s")
    asyncio.run(main())
