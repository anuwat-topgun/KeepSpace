#!/usr/bin/env python3
"""Fail CI when any supported locale is missing or carries stale catalogue keys."""

from __future__ import annotations

import json
import re
from pathlib import Path

from build import LOCALES, SUPPORTED


def strings(code: str) -> dict[str, str]:
    return json.loads((LOCALES / f"{code}.json").read_text(encoding="utf-8"))["strings"]


def placeholders(value: str) -> list[str]:
    """Return format argument types, treating Apple %@ and Java %s as equivalent."""
    found = re.findall(r"%(?!%)(?:\d+\$)?,?([@ds])", value)
    return ["s" if kind == "@" else kind for kind in found]


def main() -> None:
    expected = set(strings("en"))
    problems: list[str] = []
    for code in SUPPORTED:
        actual = strings(code)
        missing = sorted(expected - set(actual))
        extra = sorted(set(actual) - expected)
        empty = sorted(key for key in expected if not actual.get(key, "").strip())
        if missing:
            problems.append(f"{code}: missing {', '.join(missing)}")
        if extra:
            problems.append(f"{code}: stale {', '.join(extra)}")
        if empty:
            problems.append(f"{code}: empty {', '.join(empty)}")
        for key in expected:
            source_args = placeholders(strings("en")[key])
            translated_args = placeholders(actual.get(key, ""))
            if source_args != translated_args:
                problems.append(
                    f"{code}: placeholder mismatch in {key}: "
                    f"expected {source_args}, got {translated_args}"
                )
    if problems:
        raise SystemExit("Localization validation failed:\n" + "\n".join(problems))
    print(f"validated {len(SUPPORTED)} complete locale catalogues with {len(expected)} keys")


if __name__ == "__main__":
    main()
