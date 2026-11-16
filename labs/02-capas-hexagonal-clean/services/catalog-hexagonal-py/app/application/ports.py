"""Puertos de SALIDA: lo que la aplicación necesita del mundo exterior.

Es un Protocol (tipado estructural): un adaptador lo cumple si tiene estos métodos,
sin heredar de nada. La aplicación lo define en sus términos y los adaptadores lo implementan.
"""
from typing import Protocol
from uuid import UUID

from app.domain.invention import Invention


class InventionRepository(Protocol):
    name: str  # nombre del adaptador, solo para /api/info

    def save(self, invention: Invention) -> None: ...

    def find_by_id(self, invention_id: UUID) -> Invention | None: ...

    def find_by_inventor(self, inventor_id: str) -> list[Invention]: ...
