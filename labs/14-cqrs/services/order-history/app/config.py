from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    mongo_url: str = "mongodb://localhost:27017"
    mongo_db: str = "order_history"
    kafka_bootstrap_servers: str = "localhost:9092"
    topics: str = "orders.events,payments.events,shipping.events"
    # Cambiar la versión de la proyección la RECONSTRUYE: la colección se vacía y el proyector
    # vuelve a leer los topics desde el principio. Ver README, "Reconstruir la vista".
    projection_version: int = 1
    # Demora artificial por evento, para que la consistencia eventual se VEA en la web.
    projection_delay_ms: int = 1500
    group_id: str = "order-history"
