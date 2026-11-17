"""El CLIENTE del legado: solo transporte (HTTP + bytes). No interpreta nada: eso es del traductor."""
import httpx

from app.domain import CarrierUnavailable

XML_LATIN1 = {"Content-Type": "text/xml; charset=ISO-8859-1"}


class LegacyCarrierClient:
    def __init__(self, http: httpx.Client):
        self.http = http

    def post_dispatch(self, body: bytes) -> bytes:
        return self._call("POST", "/servicios/despacho", content=body, headers=XML_LATIN1)

    def get_tracking(self, guide: str) -> bytes:
        return self._call("GET", "/servicios/consulta", params={"guia": guide})

    def _call(self, method: str, path: str, **kwargs) -> bytes:
        try:
            response = self.http.request(method, path, **kwargs)
            response.raise_for_status()
        except httpx.HTTPError as e:
            raise CarrierUnavailable(f"El transportista no responde: {e}") from e
        return response.content   # bytes: el XML declara su propio encoding (ISO-8859-1)
