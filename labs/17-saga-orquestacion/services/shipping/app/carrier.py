"""Transportista externo SIMULADO, con un control de caos.

  ok     → programa el envío y devuelve un código de seguimiento
  down   → no responde (falla TRANSITORIA): hay que reintentar, no compensar  (caso S4)
  reject → rechaza el envío para siempre (falla DEFINITIVA): hay que compensar (caso S5)
"""

import uuid
from typing import Literal

Mode = Literal["ok", "down", "reject"]


class CarrierDown(Exception):
    pass


class CarrierRejected(Exception):
    pass


class Carrier:
    def __init__(self, mode: Mode = "ok"):
        self.mode: Mode = mode

    def schedule(self, order_id: str) -> str:
        if self.mode == "down":
            raise CarrierDown("El transportista no responde")
        if self.mode == "reject":
            raise CarrierRejected("El transportista no cubre la dirección de entrega")
        return f"TRK-{uuid.uuid4().hex[:8].upper()}"
