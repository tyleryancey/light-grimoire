from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from . import fetch as fetch_mod
from .emit import ASSETS_DIR, emit
from .legal import write_legal
from .normalize import fivebits_2014, open5e_v1_sections
from .validate import validate


def cmd_fetch(_args) -> None:
    for name, path in fetch_mod.fetch_all().items():
        print(f"{name}: {path}")


def _build(record: bool) -> dict:
    lock = fetch_mod.load_lock()
    paths = fetch_mod.fetch_all(record=record)
    comp = fivebits_2014.normalize(paths["fivebits-2014"])
    extra_rules, extra_sections = open5e_v1_sections.supplement(paths["open5e-v1-wotc-srd"])
    comp["rules"] = sorted(comp["rules"] + extra_rules, key=lambda r: r["key"])
    comp["rule_sections"] = sorted(comp["rule_sections"] + extra_sections, key=lambda r: r["key"])
    validate(comp, lock["expectedCounts"])
    cross = json.loads((paths["open5e-v1-wotc-srd"] / "Spell.json").read_text(encoding="utf-8"))
    if len(cross) != len(comp["spells"]):
        raise SystemExit(f"cross-check: Open5e v1 has {len(cross)} spells, we emit {len(comp['spells'])}")
    return {"lock": lock, "comp": comp, "paths": paths}


def cmd_build(args) -> None:
    b = _build(record=True)
    index = emit(b["comp"], b["lock"])
    write_legal(b["paths"]["cc-by-4.0-legalcode"] / "CC-BY-4.0.txt")
    total = sum(f["bytes"] for f in index["files"].values())
    print(f"emitted {len(index['files'])} files, {total/1e6:.2f} MB, bundle {index['bundleSha256'][:12]}")
    for name, f in index["files"].items():
        print(f"  {name:24s} {f['count']:5d} records {f['bytes']/1e3:8.1f} kB")


def cmd_validate(_args) -> None:
    _build(record=False)
    print("valid")


def cmd_fixtures(_args) -> None:
    from .reference import fixtures

    written = fixtures.write_all(ASSETS_DIR)
    for p in written:
        print(f"wrote {p}")


def cmd_all(args) -> None:
    cmd_build(args)
    cmd_fixtures(args)


def main(argv: list[str] | None = None) -> None:
    p = argparse.ArgumentParser(prog="pipeline")
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("fetch", help="download pinned inputs into pipeline/cache").set_defaults(fn=cmd_fetch)
    sub.add_parser("build", help="normalize + validate + emit assets + legal files").set_defaults(fn=cmd_build)
    sub.add_parser("validate", help="normalize + validate only (no writes)").set_defaults(fn=cmd_validate)
    sub.add_parser("fixtures", help="regenerate golden fixtures from the reference rules").set_defaults(fn=cmd_fixtures)
    sub.add_parser("all", help="build + fixtures").set_defaults(fn=cmd_all)
    args = p.parse_args(argv)
    args.fn(args)


if __name__ == "__main__":
    main()
