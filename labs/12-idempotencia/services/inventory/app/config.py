from pydantic import Field
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    database_url: str = Field(min_length=1)
    kafka_bootstrap_servers: str | None = None
    kafka_topic: str = "orders.events"
    kafka_dlq_topic: str = "orders.events.dlq"
    kafka_group_id: str = "inventory"
    retry_backoff_seconds: float = 2.0
    log_level: str = "INFO"

    # true  → Idempotent Consumer: cada eventId se procesa UNA vez.
    # false → versión ingenua: un evento repetido se procesa de nuevo.
    idempotent_consumer: bool = True
    # Experimento: el proceso muere DESPUÉS de procesar el primer mensaje y ANTES
    # de confirmar el offset en Kafka. Al reiniciar, Kafka se lo vuelve a entregar.
    chaos_crash_before_commit: bool = False
