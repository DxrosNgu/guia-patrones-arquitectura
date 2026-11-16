import pytest
from pydantic import ValidationError

from app.config import Settings


def test_missing_app_env_fails_fast(monkeypatch):
    monkeypatch.delenv("APP_ENV", raising=False)
    monkeypatch.setenv("SUPPLIER_API_KEY", "abc123")

    with pytest.raises(ValidationError) as error:
        Settings()

    assert "app_env" in str(error.value)


def test_missing_supplier_key_fails_fast(monkeypatch):
    monkeypatch.setenv("APP_ENV", "test")
    monkeypatch.delenv("SUPPLIER_API_KEY", raising=False)

    with pytest.raises(ValidationError) as error:
        Settings()

    assert "supplier_api_key" in str(error.value)


def test_empty_app_env_is_rejected(monkeypatch):
    monkeypatch.setenv("APP_ENV", "")
    monkeypatch.setenv("SUPPLIER_API_KEY", "abc123")

    with pytest.raises(ValidationError):
        Settings()


def test_reads_values_from_environment(monkeypatch):
    monkeypatch.setenv("APP_ENV", "dev")
    monkeypatch.setenv("SUPPLIER_API_KEY", "abc123")
    monkeypatch.setenv("LOG_LEVEL", "DEBUG")
    monkeypatch.setenv("LOG_JSON", "true")

    settings = Settings()

    assert settings.app_env == "dev"
    assert settings.log_level == "DEBUG"
    assert settings.log_json is True
    assert settings.supplier_api_key.get_secret_value() == "abc123"
