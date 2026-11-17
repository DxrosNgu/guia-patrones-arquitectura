"""TRANSPORTISTA LEGADO (simulado): "Rápido SAC", sistema de 2003.

Así son las integraciones reales con sistemas viejos, y por eso existe el Anti-Corruption Layer:
- XML en ISO-8859-1 (no UTF-8), con etiquetas crípticas: EST, TPO_SRV, FEC_EST, COD_RET.
- Pesos en LIBRAS con coma decimal ("4,41") y fechas en dd/MM/yy ("19/11/26").
- Destinos por UBIGEO (código de distrito del INEI), dirección en mayúsculas, sin tildes, 40 caracteres.
- Errores con HTTP 200 y un código de retorno: COD_RET=07 "PESO EXCEDE LIMITE".

Y un día, la versión 2 del legado cambia los códigos de estado (PUT /admin/version {"version": 2}).

El simulador NO es parte del patrón: es el sistema que el patrón aísla.
"""
from datetime import date, datetime, timedelta
from decimal import Decimal, InvalidOperation
from xml.etree.ElementTree import Element, SubElement, tostring
from zoneinfo import ZoneInfo

from defusedxml.ElementTree import ParseError, fromstring
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse, Response

LIMA = ZoneInfo("America/Lima")
COVERED_UBIGEOS = {"150101", "150104", "150122", "150131", "150140"}  # Lima, Barranco, Miraflores, San Isidro, Surco
DAYS_BY_SERVICE = {"EXP": 1, "STD": 3, "ECO": 5}
MAX_WEIGHT_LB = Decimal(110)

# Estados por versión del legado. En la v2, "EN RUTA" (3) se partió en "EN TRANSITO" (7) y "EN REPARTO" (8).
STATES = {
    1: {"sequence": ["1", "2", "3", "4"],
        "names": {"1": "REGISTRADO", "2": "RECOGIDO", "3": "EN RUTA", "4": "ENTREGADO", "5": "DEVUELTO", "9": "INCIDENCIA"}},
    2: {"sequence": ["1", "2", "7", "8", "4"],
        "names": {"1": "REGISTRADO", "2": "RECOGIDO", "7": "EN TRANSITO", "8": "EN REPARTO", "4": "ENTREGADO",
                  "5": "DEVUELTO", "9": "INCIDENCIA"}},
}
V1_TO_V2 = {"3": "7"}


def _xml(fields: dict[str, str | None]) -> Response:
    root = Element("RESPUESTA")
    for tag, value in fields.items():
        SubElement(root, tag).text = value
    return Response(tostring(root, encoding="ISO-8859-1", xml_declaration=True),
                    media_type="text/xml; charset=ISO-8859-1")


def _error(code: str, message: str) -> Response:
    # El legado responde HTTP 200 también cuando falla: el error va en COD_RET.
    return _xml({"COD_RET": code, "DES_RET": message})


def _ddmmyy(d: date) -> str:
    return d.strftime("%d/%m/%y")


def create_app() -> FastAPI:
    app = FastAPI(title="Rápido SAC · sistema de despachos (legado simulado)")
    guides: dict[str, dict] = {}
    state = {"version": 1, "next": 1}

    def today() -> date:
        return datetime.now(LIMA).date()

    @app.post("/servicios/despacho")
    async def dispatch(request: Request):
        try:
            req = fromstring(await request.body())
        except ParseError:
            return _error("01", "XML MAL FORMADO")
        get = lambda tag: (req.findtext(tag) or "").strip()
        service, ubigeo, address = get("TPO_SRV"), get("UBIGEO"), get("DIR")
        try:
            weight = Decimal(get("PESO_LB").replace(",", "."))
        except InvalidOperation:
            return _error("02", "PESO INVALIDO")
        if service not in DAYS_BY_SERVICE:
            return _error("03", "TIPO SERVICIO INVALIDO")
        if weight > MAX_WEIGHT_LB:
            return _error("07", "PESO EXCEDE LIMITE")
        if ubigeo not in COVERED_UBIGEOS:
            return _error("12", "UBIGEO SIN COBERTURA")
        if not address or len(address) > 40 or address != address.upper():
            return _error("13", "DIRECCION INVALIDA")
        guide = f"RS{state['next']:07d}"
        state["next"] += 1
        eta = today() + timedelta(days=DAYS_BY_SERVICE[service])
        guides[guide] = {"est": "1", "eta": eta, "delivered": None, "obs": "REGISTRADO EN SISTEMA", "ref": get("REF")}
        return _xml({"COD_RET": "00", "DES_RET": "OK", "NRO_GUIA": guide, "FEC_EST": _ddmmyy(eta)})

    @app.get("/servicios/consulta")
    def track(guia: str):
        guide = guides.get(guia)
        if guide is None:
            return _error("04", "GUIA NO EXISTE")
        est = guide["est"]
        if state["version"] == 2:
            est = V1_TO_V2.get(est, est)
        return _xml({"COD_RET": "00", "DES_RET": "OK", "NRO_GUIA": guia, "EST": est,
                     "DES_EST": STATES[state["version"]]["names"].get(est, "?"), "FEC_EST": _ddmmyy(guide["eta"]),
                     "FEC_ENT": _ddmmyy(guide["delivered"]) if guide["delivered"] else "", "OBS": guide["obs"]})

    # ── Administración del SIMULADOR (el legado real no tiene esto) ──────────

    @app.post("/admin/guias/{guia}/avanzar")
    def advance(guia: str):
        guide = guides.get(guia)
        if guide is None:
            return JSONResponse({"message": "No existe"}, status_code=404)
        sequence = STATES[state["version"]]["sequence"]
        current = V1_TO_V2.get(guide["est"], guide["est"]) if state["version"] == 2 else guide["est"]
        if current in sequence and current != sequence[-1]:
            guide["est"] = sequence[sequence.index(current) + 1]
            guide["obs"] = "EN CAMINO AL DESTINO"
            if guide["est"] == "4":
                guide["delivered"] = today()
                guide["obs"] = "RECIBIDO POR: SRA. MUÑOZ"   # Ñ: una de las razones del ISO-8859-1
        return {"guia": guia, "est": guide["est"]}

    @app.put("/admin/guias/{guia}/estado")
    async def set_state(guia: str, request: Request):
        body = await request.json()
        guides[guia]["est"], guides[guia]["obs"] = body["est"], body.get("obs", "")
        return {"guia": guia, "est": body["est"]}

    @app.put("/admin/version")
    async def set_version(request: Request):
        version = int((await request.json())["version"])
        if version not in STATES:
            return JSONResponse({"message": "Versiones: 1 o 2"}, status_code=400)
        state["version"] = version
        return {"version": version, "states": STATES[version]["names"]}

    @app.get("/admin/version")
    def get_version():
        return {"version": state["version"], "states": STATES[state["version"]]["names"]}

    @app.get("/health")
    def health():
        return {"status": "UP"}

    return app
