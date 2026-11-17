"""Clientes de los tres servicios. Todos piden en LOTE (?orderIds=a,b,c): una llamada por servicio, no una por pedido."""
import asyncio
from dataclasses import dataclass

import httpx


@dataclass
class Services:
    client: httpx.AsyncClient
    orders_url: str
    payments_url: str
    shipping_url: str
    timeout: float = 1.0   # segundos por llamada: un servicio lento no puede colgar la pantalla

    async def _get(self, url: str, params: dict) -> list:
        async def call() -> list:
            response = await self.client.get(url, params=params)
            response.raise_for_status()
            return response.json()

        return await asyncio.wait_for(call(), self.timeout)

    async def orders(self, customer: str) -> list:
        return await self._get(f"{self.orders_url}/api/orders", {"customerId": customer})

    async def payments(self, order_ids: list[str]) -> list:
        return await self._get(f"{self.payments_url}/api/payments", {"orderIds": ",".join(order_ids)})

    async def shipments(self, order_ids: list[str]) -> list:
        return await self._get(f"{self.shipping_url}/api/shipments", {"orderIds": ",".join(order_ids)})
