from fastapi.testclient import TestClient

from app.main import create_app

REQUEST = ("<?xml version='1.0' encoding='ISO-8859-1'?><SOLICITUD><TPO_SRV>EXP</TPO_SRV><PESO_LB>4,41</PESO_LB>"
           "<UBIGEO>150122</UBIGEO><DIR>AV. LARCO 1150</DIR><REF>o-1</REF></SOLICITUD>")


def field(response, tag):
    return response.text.split(f"<{tag}>")[1].split(f"</{tag}>")[0] if f"<{tag}>" in response.text else None


def test_dispatch_answers_latin1_xml_with_a_guide():
    client = TestClient(create_app())
    r = client.post("/servicios/despacho", content=REQUEST.encode("latin-1"))
    assert r.status_code == 200
    assert r.headers["content-type"] == "text/xml; charset=ISO-8859-1"
    assert field(r, "COD_RET") == "00"
    assert field(r, "NRO_GUIA") == "RS0000001"


def test_errors_are_http_200_with_a_return_code():
    client = TestClient(create_app())
    r = client.post("/servicios/despacho", content=REQUEST.replace("4,41", "250,00").encode("latin-1"))
    assert r.status_code == 200
    assert (field(r, "COD_RET"), field(r, "DES_RET")) == ("07", "PESO EXCEDE LIMITE")
    r = client.post("/servicios/despacho", content=REQUEST.replace("150122", "040101").encode("latin-1"))
    assert field(r, "COD_RET") == "12"


def test_version_2_renames_the_in_transit_state():
    client = TestClient(create_app())
    client.post("/servicios/despacho", content=REQUEST.encode("latin-1"))
    for _ in range(2):
        client.post("/admin/guias/RS0000001/avanzar")
    assert field(client.get("/servicios/consulta", params={"guia": "RS0000001"}), "EST") == "3"

    client.put("/admin/version", json={"version": 2})

    assert field(client.get("/servicios/consulta", params={"guia": "RS0000001"}), "EST") == "7"
    client.post("/admin/guias/RS0000001/avanzar")
    assert field(client.get("/servicios/consulta", params={"guia": "RS0000001"}), "EST") == "8"


def test_delivered_has_a_delivery_date_and_a_latin1_note():
    client = TestClient(create_app())
    client.post("/servicios/despacho", content=REQUEST.encode("latin-1"))
    for _ in range(3):
        client.post("/admin/guias/RS0000001/avanzar")
    r = client.get("/servicios/consulta", params={"guia": "RS0000001"})
    assert field(r, "EST") == "4"
    assert field(r, "FEC_ENT") is not None
    assert "MUÑOZ".encode("latin-1") in r.content
