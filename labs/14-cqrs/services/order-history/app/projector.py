"""El PROYECTOR: un proceso aparte que consume los tres topics y actualiza la vista.

    python -m app.projector

Está separado de la API a propósito: si el proyector se detiene, la API sigue respondiendo
(con datos que se van quedando viejos), y cuando vuelve, se pone al día desde su último offset.
"""
import asyncio
import json
import logging
import signal

from aiokafka import AIOKafkaConsumer, ConsumerRebalanceListener
from pymongo import MongoClient

from app.config import Settings
from app.projection import Projection

log = logging.getLogger("projector")


class RewindOnce(ConsumerRebalanceListener):
    """Tras vaciar la vista, la primera vez que Kafka le asigna particiones, vuelve al offset 0.
    Sin esto, el consumer group seguiría desde su último offset y la vista quedaría vacía."""

    def __init__(self, consumer: AIOKafkaConsumer, rewind: bool):
        self.consumer, self.rewind = consumer, rewind

    async def on_partitions_revoked(self, revoked):
        pass

    async def on_partitions_assigned(self, assigned):
        if self.rewind and assigned:
            await self.consumer.seek_to_beginning(*assigned)
            self.rewind = False


async def run(settings: Settings) -> None:
    projection = Projection(MongoClient(settings.mongo_url)[settings.mongo_db])
    rebuild = projection.reset_if_version_changed(settings.projection_version)
    if rebuild:
        log.warning("Proyección v%d: vista vacía, se reconstruye leyendo los topics desde el principio",
                    settings.projection_version)
    consumer = AIOKafkaConsumer(bootstrap_servers=settings.kafka_bootstrap_servers, group_id=settings.group_id,
                                enable_auto_commit=False, auto_offset_reset="earliest")
    consumer.subscribe(settings.topics.split(","), listener=RewindOnce(consumer, rebuild))
    await consumer.start()
    log.info("Proyector %s escuchando %s", settings.group_id, settings.topics)
    try:
        async for message in consumer:
            try:
                event = json.loads(message.value)
            except json.JSONDecodeError:
                log.warning("Mensaje inválido en %s, se ignora", message.topic)
                await consumer.commit()
                continue
            await asyncio.sleep(settings.projection_delay_ms / 1000)
            applied = await asyncio.to_thread(projection.apply, event)
            log.info("%s %s %s", "✔" if applied else "↩ ya aplicado:", event.get("type"), event.get("aggregateId"))
            await consumer.commit()
    finally:
        await consumer.stop()


async def main() -> None:
    # `docker stop` manda SIGTERM. Sin este handler, Python (PID 1 en el contenedor) lo ignora, Docker lo
    # mata a los 10 s y el consumidor NO sale del consumer group: Kafka espera el session timeout para
    # repartir sus particiones. Con el handler, el `finally` de run() cierra el consumidor y sale al instante.
    asyncio.get_running_loop().add_signal_handler(signal.SIGTERM, asyncio.current_task().cancel)
    try:
        await run(Settings())
    except asyncio.CancelledError:
        log.info("SIGTERM: proyector detenido; salió del consumer group")


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)-5s [%(name)s] %(message)s")
    asyncio.run(main())
