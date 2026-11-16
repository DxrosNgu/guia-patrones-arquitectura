"""Adaptador de ENTRADA REST: traduce HTTP ⇄ casos de uso. No contiene reglas de negocio."""
from datetime import datetime
from decimal import Decimal
from uuid import UUID

from fastapi import APIRouter, FastAPI, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel

from app.application.catalog_service import CatalogService, CreateDraft, InventionNotFound
from app.domain.invention import BusinessRuleViolation, Invention, Rule

CONFLICT_RULES = {Rule.NAME_ALREADY_USED, Rule.TOO_MANY_DRAFTS, Rule.ALREADY_PUBLISHED}


class CamelModel(BaseModel):
    """JSON en camelCase (inventorId), igual que las versiones Quarkus."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class CreateInventionRequest(CamelModel):
    inventor_id: str | None = None
    name: str | None = None
    price: Decimal | None = None
    image_url: str | None = None


class ChangeImageRequest(CamelModel):
    image_url: str | None = None


class InventionResponse(CamelModel):
    id: UUID
    inventor_id: str
    name: str
    price: float  # como número en el JSON, igual que en Java
    image_url: str | None
    status: str
    created_at: datetime

    @classmethod
    def of(cls, invention: Invention) -> "InventionResponse":
        return cls(id=invention.id, inventor_id=invention.inventor_id, name=invention.name,
                   price=float(invention.price.amount), image_url=invention.image_url,
                   status=invention.status.value, created_at=invention.created_at)


def build_router(catalog: CatalogService) -> APIRouter:
    router = APIRouter(prefix="/api/inventions")

    @router.post("", status_code=201, response_model=InventionResponse, response_model_by_alias=True)
    def create(body: CreateInventionRequest):
        invention = catalog.create_draft(CreateDraft(body.inventor_id, body.name, body.price, body.image_url))
        return InventionResponse.of(invention)

    @router.put("/{invention_id}/image", response_model=InventionResponse, response_model_by_alias=True)
    def change_image(invention_id: UUID, body: ChangeImageRequest):
        return InventionResponse.of(catalog.change_image(invention_id, body.image_url))

    @router.post("/{invention_id}/publish", response_model=InventionResponse, response_model_by_alias=True)
    def publish(invention_id: UUID):
        return InventionResponse.of(catalog.publish(invention_id))

    @router.get("/{invention_id}", response_model=InventionResponse, response_model_by_alias=True)
    def get(invention_id: UUID):
        return InventionResponse.of(catalog.get(invention_id))

    @router.get("", response_model=list[InventionResponse], response_model_by_alias=True)
    def list_by_inventor(inventorId: str):  # noqa: N803 (nombre del query param)
        return [InventionResponse.of(i) for i in catalog.list_by_inventor(inventorId)]

    return router


def register_error_handlers(app: FastAPI) -> None:
    """Traduce las excepciones del dominio a HTTP. Es decisión del adaptador, no del dominio."""

    @app.exception_handler(BusinessRuleViolation)
    def business_rule(_: Request, error: BusinessRuleViolation):
        status = 409 if error.rule in CONFLICT_RULES else 422
        return JSONResponse(status_code=status, content={"code": error.rule.value, "message": error.message})

    @app.exception_handler(InventionNotFound)
    def not_found(_: Request, error: InventionNotFound):
        return JSONResponse(status_code=404, content={"code": "NOT_FOUND", "message": str(error)})
