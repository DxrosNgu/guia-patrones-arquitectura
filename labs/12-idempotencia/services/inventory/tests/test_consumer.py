import asyncio

from app.consumer import process_message
from app.events import InvalidEvent, TransientError


class FakeDlq:
    def __init__(self):
        self.sent = []

    async def send(self, raw: bytes, reason: str):
        self.sent.append((raw, reason))


class FlakyHandler:
    """Falla `failures` veces con un error transitorio y después funciona."""

    def __init__(self, failures=0, error=None):
        self.failures, self.error, self.calls = failures, error, 0

    def handle(self, raw):
        self.calls += 1
        if self.error:
            raise self.error
        if self.calls <= self.failures:
            raise TransientError("todavía no")
        return "RESERVED"


def run(coro):
    return asyncio.run(coro)


def test_a_valid_message_is_handled_once():
    handler, dlq = FlakyHandler(), FakeDlq()

    outcome = run(process_message(b"{}", handler, dlq, backoff_seconds=0))

    assert outcome == "RESERVED"
    assert handler.calls == 1
    assert dlq.sent == []


def test_a_poison_message_goes_to_the_dlq_and_does_not_block_the_rest():
    handler, dlq = FlakyHandler(error=InvalidEvent("no es JSON")), FakeDlq()

    outcome = run(process_message(b"basura", handler, dlq, backoff_seconds=0))

    assert outcome == "DLQ"
    assert dlq.sent == [(b"basura", "no es JSON")]


def test_a_transient_failure_is_retried_until_it_works():
    handler, dlq = FlakyHandler(failures=3), FakeDlq()

    outcome = run(process_message(b"{}", handler, dlq, backoff_seconds=0))

    assert outcome == "RESERVED"
    assert handler.calls == 4
    assert dlq.sent == []
