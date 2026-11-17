import socket

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # Nombre de esta instancia (por defecto, el hostname del contenedor: distinto en cada réplica).
    instance_name: str = socket.gethostname()
    service_name: str = "inventory"
    port: int = 8000
    # Si no hay CONSUL_URL, la instancia no se registra (tests, modo desarrollo).
    consul_url: str | None = None
    # La IP con la que OTROS llegan a esta instancia. Por defecto, la IP del contenedor.
    advertise_address: str | None = None
