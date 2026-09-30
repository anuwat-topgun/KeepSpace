#!/usr/bin/env python3
"""Populate missing locale entries using Google Translate's public UI endpoint.

This is a bootstrap aid, not a runtime dependency.  Generated translations are checked
in and can be reviewed or corrected directly in the locale JSON files.
"""

from __future__ import annotations

import json
import subprocess
import time
from pathlib import Path

from build import LOCALES, SUPPORTED, load

TARGET = {"zh-Hans": "zh-CN", "zh-Hant": "zh-TW"}
SEPARATOR = "\n__KSSEP_4F19__\n"
BATCH = 100
PROTECTED = {
    "KeepSpace": "__KS_BRAND__", "Google Drive": "__KS_GOOGLE_DRIVE__",
    "OneDrive": "__KS_ONEDRIVE__", "Wi-Fi": "__KS_WIFI__", "HTTPS": "__KS_HTTPS__",
    "iOS": "__KS_IOS__", "Android": "__KS_ANDROID__", "AI": "__KS_AI__",
}


def protect(value: str) -> str:
    for source, token in PROTECTED.items():
        value = value.replace(source, token)
    return value


def restore(value: str) -> str:
    for source, token in PROTECTED.items():
        value = value.replace(token, source).replace(token.lower(), source)
    return value


def request(values: list[str], target: str) -> list[str]:
    query = SEPARATOR.join(protect(value) for value in values)
    last_error: Exception | None = None
    for attempt in range(5):
        try:
            result = subprocess.run(
                ["curl", "-fsS", "--get", "https://translate.google.com/translate_a/single",
                 "-H", "User-Agent: Mozilla/5.0", "--data-urlencode", "client=gtx",
                 "--data-urlencode", "sl=en", "--data-urlencode", f"tl={target}",
                 "--data-urlencode", "dt=t", "--data-urlencode", f"q={query}"],
                check=True, capture_output=True, text=True, timeout=60,
            )
            payload = json.loads(result.stdout)
            text = "".join(part[0] for part in payload[0] if part[0])
            result = [restore(item.strip()) for item in text.split(SEPARATOR)]
            if len(result) != len(values):
                raise ValueError(f"separator mismatch: expected {len(values)}, got {len(result)}")
            return result
        except Exception as error:
            last_error = error
            time.sleep(1.5 * (attempt + 1))
    raise RuntimeError(f"translation failed for {target}: {last_error}")


def main() -> None:
    base = load("en")
    for code in SUPPORTED:
        if code == "en":
            continue
        path = LOCALES / f"{code}.json"
        current = json.loads(path.read_text(encoding="utf-8")).get("strings", {}) if path.exists() else {}
        # The English catalogue is authoritative. Remove strings that disappeared from source so
        # validation can guarantee every locale has exactly the same key set.
        current = {key: value for key, value in current.items() if key in base}
        missing = [key for key in base if key not in current]
        for offset in range(0, len(missing), BATCH):
            keys = missing[offset:offset + BATCH]
            translated = request([base[key] for key in keys], TARGET.get(code, code))
            current.update(zip(keys, translated))
            path.write_text(json.dumps({"strings": current}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            print(f"{code}: {min(offset + BATCH, len(missing))}/{len(missing)}")
            time.sleep(0.15)
        if not missing:
            path.write_text(json.dumps({"strings": current}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
