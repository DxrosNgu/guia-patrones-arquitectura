"""Self Registration: cada instancia se anota sola en Consul al arrancar y se borra al apagarse.

Consul además le hace un health check cada 2 s. Si la instancia deja de responder (se colgó,
o la mataron con kill -9 y no alcanzó a borrarse), Consul la marca "critical" y los clientes
dejan de verla. Si sigue así un minuto, Consul la borra del registro.
"""
import logging

import httpx

log = logging.getLogger("inventory")


def registration(service: str, instance: str, address: str, port: int) -> dict:
    return {
        "ID": instance,
        "Name": service,
        "Address": address,
        "Port": port,
        "Check": {
            "HTTP": f"http://{address}:{port}/health",
            "Interval": "2s",
            "Timeout": "1s",
            # Si la instancia muere sin despedirse, Consul la saca del registro a este tiempo.
            "DeregisterCriticalServiceAfter": "1m",
        },
    }


class ConsulRegistry:
    def __init__(self, consul_url: str, transport: httpx.BaseTransport | None = None):
        self._client = httpx.Client(base_url=consul_url, timeout=3.0, transport=transport)

    def register(self, body: dict) -> None:
        self._client.put("/v1/agent/service/register", json=body).raise_for_status()
        log.info("Registrada en Consul como %s (%s:%s)", body["ID"], body["Address"], body["Port"])

    def deregister(self, instance: str) -> None:
        try:
            self._client.put(f"/v1/agent/service/deregister/{instance}").raise_for_status()
            log.info("Borrada de Consul: %s", instance)
        except httpx.HTTPError as error:
            log.warning("No pude borrarme de Consul (%s): el health check lo hará por mí", error)
