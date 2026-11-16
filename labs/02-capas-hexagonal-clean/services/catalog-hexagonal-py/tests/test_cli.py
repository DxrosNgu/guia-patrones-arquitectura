"""Un segundo adaptador de ENTRADA (línea de comandos) que usa los mismos casos de uso."""
import json

from app.adapters.cli import main


def test_cli_creates_and_publishes(capsys):
    main(["--persistence", "memory", "demo"])

    lines = [json.loads(line) for line in capsys.readouterr().out.strip().splitlines()]
    assert [line["status"] for line in lines] == ["DRAFT", "PUBLISHED"]
