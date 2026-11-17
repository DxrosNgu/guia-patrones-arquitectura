"""La versión INGENUA: sin Anti-Corruption Layer. El modelo del legado atraviesa el servicio entero.

- Quien llama tiene que saber que el transportista quiere libras con coma, UBIGEO y "EXP".
- La respuesta devuelve EST, FEC_EST y COD_RET tal cual: el frontend termina con `EST === "3" ? "En camino"`.
- La regla "puede calificar la entrega" se escribe con un código del legado (EST == "4").
- Un error llega como HTTP 200 con COD_RET="07": quien llama tiene que conocer también esos códigos.
- El XML se arma con un f-string: una dirección con "&" lo rompe.
Cuando el legado cambia un código, hay que buscar cada uno de esos lugares, aquí y en cada cliente.
"""
from datetime import datetime

import httpx
from defusedxml.ElementTree import fromstring
from fastapi import APIRouter
from pydantic import BaseModel


class LegacyShipmentRequest(BaseModel):
    orderId: str
    TPO_SRV: str
    PESO_LB: str
    UBIGEO: str
    DIR: str


def router(http: httpx.Client) -> APIRouter:
    api = APIRouter(prefix="/api/naive/shipments", tags=["versión ingenua"])

    def fields(body: bytes) -> dict[str, str]:
        return {child.tag: (child.text or "") for child in fromstring(body)}

    @api.post("")
    def create(request: LegacyShipmentRequest):
        xml = (f"<?xml version='1.0' encoding='ISO-8859-1'?><SOLICITUD><TPO_SRV>{request.TPO_SRV}</TPO_SRV>"
               f"<PESO_LB>{request.PESO_LB}</PESO_LB><UBIGEO>{request.UBIGEO}</UBIGEO><DIR>{request.DIR}</DIR>"
               f"<REF>{request.orderId}</REF></SOLICITUD>")
        return fields(http.post("/servicios/despacho", content=xml.encode("latin-1")).content)

    @api.get("/{guia}")
    def get(guia: str):
        legacy = fields(http.get("/servicios/consulta", params={"guia": guia}).content)
        legacy["puedeCalificar"] = legacy.get("EST") == "4"      # regla de negocio escrita con un código ajeno
        if legacy.get("FEC_EST"):
            eta = datetime.strptime(legacy["FEC_EST"], "%d/%m/%y").date()
            legacy["diasParaLlegar"] = (eta - datetime.now().date()).days
        return legacy

    return api
