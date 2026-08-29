"""Emit the validated compendium as byte-stable JSON chunks under
tool/src/main/assets/compendium/, plus index.json (counts, hashes, provenance).

Why JSON chunks and not a .db: Light's server builder only extracts assets with an
allow-listed extension (.json .txt .md .bin .dat .csv …) and aborts on anything else,
with a 5 MiB per-file cap (light-sdk builder/lightbuilder/allowlist.py). The tool
imports these into Room on first launch, keyed by index.json's bundle hash.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
ASSETS_DIR = REPO_ROOT / "tool" / "src" / "main" / "assets" / "compendium"
MAX_FILE_BYTES = 5 * 1024 * 1024  # Light builder cap per file (allowlist.py MAX_FILE_SIZE_BYTES)
SCHEMA_VERSION = 1


def _dump(obj) -> bytes:
    # sort_keys + fixed indent + no trailing spaces = reproducible bytes across machines.
    return (json.dumps(obj, indent=1, sort_keys=True, ensure_ascii=False) + "\n").encode("utf-8")


def emit(comp: dict, lock: dict, out_dir: Path = ASSETS_DIR) -> dict:
    out_dir.mkdir(parents=True, exist_ok=True)
    # Remove stale chunks so a renamed kind can't linger.
    for old in out_dir.glob("*.json"):
        old.unlink()
    files: dict[str, dict] = {}
    for kind in sorted(comp):
        data = _dump(comp[kind])
        if len(data) > MAX_FILE_BYTES:
            raise RuntimeError(f"{kind}.json is {len(data)} bytes > Light builder cap {MAX_FILE_BYTES}; split it")
        path = out_dir / f"{kind}.json"
        path.write_bytes(data)
        files[f"{kind}.json"] = {"bytes": len(data), "sha256": hashlib.sha256(data).hexdigest(), "count": len(comp[kind])}
    bundle_hash = hashlib.sha256("".join(f"{k}:{v['sha256']}" for k, v in sorted(files.items())).encode()).hexdigest()
    index = {
        "schemaVersion": SCHEMA_VERSION,
        "edition": lock["edition"],
        "srdVersion": lock["srdVersion"],
        "license": "CC-BY-4.0",
        "attribution": "assets/legal/ATTRIBUTION.md",
        "bundleSha256": bundle_hash,
        "files": files,
        "sources": {
            name: {k: v for k, v in src.items() if k in ("repo", "commit", "path", "url", "role", "contentLicense")}
            for name, src in lock["sources"].items()
        },
    }
    (out_dir / "index.json").write_bytes(_dump(index))
    return index
