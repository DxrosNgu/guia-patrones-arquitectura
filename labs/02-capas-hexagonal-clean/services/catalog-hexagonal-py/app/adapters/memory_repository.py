"""Adaptador de salida en memoria: para tests y para desarrollar sin base de datos."""
import copy
from uuid import UUID

from app.domain.invention import Invention


class InMemoryInventionRepository:
    name = "memory"

    def __init__(self):
        self._store: dict[UUID, Invention] = {}

    def save(self, invention: Invention) -> None:
        # Se guarda una copia, como haría una base de datos real.
        self._store[invention.id] = copy.deepcopy(invention)

    def find_by_id(self, invention_id: UUID) -> Invention | None:
        found = self._store.get(invention_id)
        return copy.deepcopy(found) if found else None

    def find_by_inventor(self, inventor_id: str) -> list[Invention]:
        found = [i for i in self._store.values() if i.inventor_id == inventor_id]
        return [copy.deepcopy(i) for i in sorted(found, key=lambda i: i.created_at)]
