"""Fetch pinned upstream inputs into pipeline/cache/<source>@<commit>/.

Desktop-only. Light's builder never runs this: it compiles `tool/` from the committed
assets that `emit.py` writes. Reproducibility comes from the pinned commits in
`sources.lock.json` plus the sha256 map that this module records on first fetch and
verifies on every later one.
"""
from __future__ import annotations

import hashlib
import json
import sys
import urllib.request
from pathlib import Path

PIPELINE_DIR = Path(__file__).resolve().parent
LOCK_PATH = PIPELINE_DIR / "sources.lock.json"
CACHE_DIR = PIPELINE_DIR / "cache"


def load_lock() -> dict:
    return json.loads(LOCK_PATH.read_text(encoding="utf-8"))


def save_lock(lock: dict) -> None:
    LOCK_PATH.write_text(json.dumps(lock, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def raw_url(source: dict, filename: str) -> str:
    repo = source["repo"].rstrip("/")
    if repo.startswith("https://github.com/"):
        owner_repo = repo[len("https://github.com/"):]
        return f"https://raw.githubusercontent.com/{owner_repo}/{source['commit']}/{source['path']}/{filename}"
    raise ValueError(f"unsupported repo host: {repo}")


def cache_dir_for(name: str, source: dict) -> Path:
    tag = source.get("commit") or "url"
    return CACHE_DIR / f"{name}@{tag[:12]}"


def _download(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "light-grimoire-pipeline/1.0"})
    with urllib.request.urlopen(req, timeout=60) as resp:  # noqa: S310 (pinned, public URLs)
        return resp.read()


def fetch_source(name: str, source: dict, *, record: bool = True) -> Path:
    """Fetch every file of one lock entry. Returns the cache directory."""
    out = cache_dir_for(name, source)
    out.mkdir(parents=True, exist_ok=True)
    hashes: dict = source.setdefault("sha256", {}) if isinstance(source.get("sha256"), dict) else {}
    if "files" in source:
        for filename in source["files"]:
            target = out / filename
            if target.exists():
                data = target.read_bytes()
            else:
                data = _download(raw_url(source, filename))
                target.write_bytes(data)
            digest = sha256_bytes(data)
            expected = hashes.get(filename)
            if expected and expected != digest:
                raise RuntimeError(
                    f"{name}/{filename}: sha256 mismatch (expected {expected[:12]}…, got {digest[:12]}…). "
                    "Upstream changed under a pinned commit, or the cache is corrupt — delete pipeline/cache and retry."
                )
            if record and not expected:
                hashes[filename] = digest
        if record:
            source["sha256"] = hashes
    elif "url" in source:
        target = out / Path(source["url"]).name
        if not target.exists():
            target.write_bytes(_download(source["url"]))
        digest = sha256_bytes(target.read_bytes())
        if source.get("sha256") and source["sha256"] != digest:
            raise RuntimeError(f"{name}: sha256 mismatch")
        if record:
            source["sha256"] = digest
    return out


def fetch_all(*, record: bool = True) -> dict[str, Path]:
    lock = load_lock()
    paths: dict[str, Path] = {}
    for name, source in lock["sources"].items():
        print(f"fetch {name} …", file=sys.stderr)
        paths[name] = fetch_source(name, source, record=record)
    if record:
        save_lock(lock)
    return paths


if __name__ == "__main__":
    for name, path in fetch_all().items():
        print(f"{name}: {path}")
