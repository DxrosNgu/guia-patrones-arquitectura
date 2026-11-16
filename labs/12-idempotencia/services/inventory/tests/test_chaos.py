from app.consumer import CrashOnce


def test_crashes_only_the_first_time(tmp_path):
    exits = []
    chaos = CrashOnce(enabled=True, marker=tmp_path / "crashed", exit=lambda code: exits.append(code))

    chaos.maybe_crash()
    chaos.maybe_crash()

    assert exits == [1]


def test_does_nothing_when_disabled(tmp_path):
    exits = []
    CrashOnce(enabled=False, marker=tmp_path / "crashed", exit=exits.append).maybe_crash()

    assert exits == []
