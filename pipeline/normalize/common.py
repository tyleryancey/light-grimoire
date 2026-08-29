"""Helpers shared by adapters."""
from __future__ import annotations

import re
from typing import Any, Iterable

EDITION = "2014"
SOURCE = "srd-5.1"
LICENSE = "CC-BY-4.0"


def stamp(key: str, name: str, xref: str | None = None) -> dict[str, Any]:
    """Common envelope every compendium record starts with."""
    rec: dict[str, Any] = {
        "key": key,
        "name": name,
        "edition": EDITION,
        "source": SOURCE,
        "license": LICENSE,
    }
    if xref:
        rec["xref"] = xref
    return rec


def paragraphs(desc: str | list[str] | None) -> str:
    """Join a 5e-bits `desc` (string or list of paragraphs) into Markdown-lite text.

    Bullet lines ("- …") stay on their own line; other paragraphs are separated by a
    blank line. Trailing/leading whitespace is stripped so output is byte-stable."""
    if desc is None:
        return ""
    if isinstance(desc, str):
        return desc.strip()
    out: list[str] = []
    for chunk in desc:
        chunk = (chunk or "").strip()
        if not chunk:
            continue
        if chunk.startswith("- ") and out and out[-1].startswith("- "):
            out[-1] = out[-1] + "\n" + chunk
        else:
            out.append(chunk)
    return "\n\n".join(out)


def ref_key(ref: dict | None) -> str | None:
    """`{"index": "x", "name": ..., "url": ...}` → "x"."""
    if not ref:
        return None
    return ref.get("index")


def ref_keys(refs: Iterable[dict] | None) -> list[str]:
    return [r["index"] for r in (refs or []) if r and "index" in r]


def option_desc(opt: dict) -> str:
    """Flatten a 5e-bits choice block into its human description (we keep the prose;
    the phone offers these as pickers built from the class/background data)."""
    return (opt.get("desc") or "").strip()


_ATTUNE_RE = re.compile(r"requires attunement(?: by (?P<by>[^)]+))?", re.IGNORECASE)


def parse_attunement(first_line: str) -> tuple[bool, str | None]:
    m = _ATTUNE_RE.search(first_line or "")
    if not m:
        return False, None
    return True, (m.group("by") or None)


def sorted_records(records: list[dict]) -> list[dict]:
    return sorted(records, key=lambda r: r["key"])
