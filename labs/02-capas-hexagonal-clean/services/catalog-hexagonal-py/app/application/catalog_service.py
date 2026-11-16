"""Casos de uso (puerto de ENTRADA): orquestan el dominio y los puertos de salida."""
from dataclasses import dataclass
from decimal import Decimal
from uuid import UUID

from app.application.ports import InventionRepository
from app.domain.draft_policy import check_can_create
from app.domain.invention import Invention


class InventionNotFound(Exception):
    def __init__(self, invention_id: UUID):
        super().__init__(f"No existe el invento {invention_id}")


@dataclass(frozen=True)
class CreateDraft:
    """Datos de entrada del caso de uso. No es el modelo de FastAPI: el CLI también lo usa."""

    inventor_id: str
    name: str
    price: Decimal | None
    image_url: str | None = None


class CatalogService:
    def __init__(self, inventions: InventionRepository):
        self._inventions = inventions

    def create_draft(self, command: CreateDraft) -> Invention:
        # 1) Reglas del propio invento.  2) Reglas que miran los otros inventos del inventor.
        invention = Invention.draft(command.inventor_id, command.name, command.price, command.image_url)
        check_can_create(invention.name, self._inventions.find_by_inventor(invention.inventor_id))
        self._inventions.save(invention)
        return invention

    def change_image(self, invention_id: UUID, image_url: str | None) -> Invention:
        invention = self.get(invention_id)
        invention.change_image(image_url)
        self._inventions.save(invention)
        return invention

    def publish(self, invention_id: UUID) -> Invention:
        invention = self.get(invention_id)
        invention.publish()
        self._inventions.save(invention)
        return invention

    def get(self, invention_id: UUID) -> Invention:
        invention = self._inventions.find_by_id(invention_id)
        if invention is None:
            raise InventionNotFound(invention_id)
        return invention

    def list_by_inventor(self, inventor_id: str) -> list[Invention]:
        return self._inventions.find_by_inventor(inventor_id)
