"""Llamar a pagos con tenacity: Retry con backoff exponencial y jitter, solo para fallas PASAJERAS.

- Pasajero (se reintenta): no hay conexión, timeout, 5xx.
- Definitivo (NO se reintenta): 4xx. Un 402 (pago rechazado) va a dar 402 siempre.

Jitter: a cada espera se le suma un azar. Si 1000 clientes fallan a la vez y todos esperan
exactamente 200 ms, vuelven a la vez y lo tumban de nuevo. Con jitter, se reparten.
"""
import asyncio
import logging
from collections.abc import Awaitable, Callable

import httpx
from tenacity import (AsyncRetrying, RetryCallState, retry_if_exception, stop_after_attempt,
                      wait_exponential_jitter)

log = logging.getLogger("checkout")


class TransientHttpError(Exception):
    def __init__(self, response: httpx.Response):
        super().__init__(f"pagos respondió {response.status_code}")
        self.response = response


def is_transient(error: BaseException) -> bool:
    return isinstance(error, (httpx.TransportError, TransientHttpError))


def _log_retry(state: RetryCallState) -> None:
    log.warning("Intento %d falló (%s); reintento en %.2f s", state.attempt_number,
                state.outcome.exception(), state.next_action.sleep if state.next_action else 0)


class PaymentsClient:
    def __init__(self, client: httpx.AsyncClient, base_url: str, attempts: int = 3, timeout: float = 2.0,
                 sleep: Callable[[float], Awaitable[None]] = asyncio.sleep):
        self.client, self.base_url, self.attempts, self.timeout, self.sleep = client, base_url, attempts, timeout, sleep

    async def pay(self, body: dict) -> tuple[httpx.Response, int]:
        retrying = AsyncRetrying(
            stop=stop_after_attempt(self.attempts),
            wait=wait_exponential_jitter(multiplier=0.2, max=2.0, jitter=0.2),   # 0,2 s, 0,4 s, 0,8 s... + azar
            retry=retry_if_exception(is_transient),
            before_sleep=_log_retry,
            sleep=self.sleep,
            reraise=True,
        )
        async for attempt in retrying:
            with attempt:
                response = await self.client.post(f"{self.base_url}/api/payments", json=body, timeout=self.timeout)
                if response.status_code >= 500:
                    raise TransientHttpError(response)
                return response, attempt.retry_state.attempt_number
        raise AssertionError("inalcanzable")
