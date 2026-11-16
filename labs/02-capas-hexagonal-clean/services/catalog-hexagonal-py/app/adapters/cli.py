"""Segundo adaptador de ENTRADA: línea de comandos. Usa los MISMOS casos de uso que REST.

    python -m app.adapters.cli create --inventor ana --name "Lámpara" --price 10
    python -m app.adapters.cli publish <id>
    python -m app.adapters.cli list --inventor ana
    python -m app.adapters.cli --persistence memory demo     # crea y publica en memoria

Agregarlo no tocó ni el dominio ni la aplicación: solo este archivo.
"""
import argparse
import json
import sys
from decimal import Decimal
from uuid import UUID

from app.application.catalog_service import CatalogService, CreateDraft
from app.config import Settings
from app.domain.invention import BusinessRuleViolation, Invention
from app.main import build_repository


def _print(invention: Invention) -> None:
    print(json.dumps({"id": str(invention.id), "name": invention.name, "status": invention.status.value,
                      "price": str(invention.price.amount)}, ensure_ascii=False))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="catalog")
    parser.add_argument("--persistence", choices=["postgres", "memory"])
    commands = parser.add_subparsers(dest="command", required=True)
    create = commands.add_parser("create")
    create.add_argument("--inventor", required=True)
    create.add_argument("--name", required=True)
    create.add_argument("--price", required=True, type=Decimal)
    create.add_argument("--image")
    commands.add_parser("publish").add_argument("id", type=UUID)
    commands.add_parser("list").add_argument("--inventor", required=True)
    commands.add_parser("demo")
    args = parser.parse_args(argv)

    settings = Settings(persistence=args.persistence) if args.persistence else Settings()
    catalog = CatalogService(build_repository(settings))
    try:
        if args.command == "create":
            _print(catalog.create_draft(CreateDraft(args.inventor, args.name, args.price, args.image)))
        elif args.command == "publish":
            _print(catalog.publish(args.id))
        elif args.command == "list":
            for invention in catalog.list_by_inventor(args.inventor):
                _print(invention)
        else:
            draft = catalog.create_draft(CreateDraft("cli", "Invento de prueba", Decimal("9.99"),
                                                     "https://img.example/demo.png"))
            _print(draft)
            _print(catalog.publish(draft.id))
    except BusinessRuleViolation as error:
        print(f"{error.rule.value}: {error.message}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
