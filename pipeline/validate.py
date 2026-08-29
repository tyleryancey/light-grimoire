"""Validate a normalized compendium: JSON Schema, expected counts, referential integrity,
and content-hygiene checks (no API URLs leaked, no empty prose where prose is required).

Fails loudly. A silent regression in the bundled rules text is the one bug the phone
can never surface, so this is the gate everything passes through before `emit`."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

from jsonschema import Draft202012Validator

PIPELINE_DIR = Path(__file__).resolve().parent
SCHEMA_PATH = PIPELINE_DIR / "schema" / "compendium.schema.json"

URL_RE = re.compile(r"/api/(2014|2024)/")


class ValidationError(Exception):
    pass


def _schema_errors(comp: dict) -> list[str]:
    schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
    v = Draft202012Validator(schema)
    errs = []
    for e in sorted(v.iter_errors(comp), key=lambda e: list(e.path)):
        path = "/".join(str(p) for p in e.path)
        errs.append(f"schema: {path}: {e.message[:160]}")
        if len(errs) >= 50:
            errs.append("schema: … (truncated)")
            break
    return errs


def _count_errors(comp: dict, expected: dict[str, int]) -> list[str]:
    errs = []
    for kind, n in expected.items():
        got = len(comp.get(kind, []))
        if got != n:
            errs.append(f"count: {kind}: expected {n}, got {got}")
    return errs


def _unique_keys(comp: dict) -> list[str]:
    errs = []
    for kind, recs in comp.items():
        seen: set[str] = set()
        for r in recs:
            if r["key"] in seen:
                errs.append(f"duplicate key: {kind}/{r['key']}")
            seen.add(r["key"])
    return errs


def _integrity(comp: dict) -> list[str]:
    """Cross-references must resolve."""
    errs = []
    keys = {kind: {r["key"] for r in recs} for kind, recs in comp.items()}
    all_class_or_sub = keys["classes"] | keys["subclasses"]

    for s in comp["spells"]:
        for c in s["classes"]:
            if c not in keys["classes"]:
                errs.append(f"spell {s['key']} → unknown class {c}")
        for c in s["subclasses"]:
            if c not in all_class_or_sub:
                errs.append(f"spell {s['key']} → unknown subclass {c}")
        if s["school"] not in keys["magic_schools"]:
            errs.append(f"spell {s['key']} → unknown school {s['school']}")
    for c in comp["classes"]:
        for sub in c["subclasses"]:
            if sub not in keys["subclasses"]:
                errs.append(f"class {c['key']} → unknown subclass {sub}")
        for lv in c["levels"]:
            for f in lv["features"]:
                if f not in keys["features"]:
                    errs.append(f"class {c['key']} L{lv['level']} → unknown feature {f}")
    for sc in comp["subclasses"]:
        if sc["classKey"] not in keys["classes"]:
            errs.append(f"subclass {sc['key']} → unknown class {sc['classKey']}")
        for lv in sc["levels"]:
            for f in lv["features"]:
                if f not in keys["features"]:
                    errs.append(f"subclass {sc['key']} L{lv['level']} → unknown feature {f}")
    for f in comp["features"]:
        if f["classKey"] not in keys["classes"]:
            errs.append(f"feature {f['key']} → unknown class {f['classKey']}")
        if f.get("subclassKey") and f["subclassKey"] not in keys["subclasses"]:
            errs.append(f"feature {f['key']} → unknown subclass {f['subclassKey']}")
    for r in comp["races"]:
        for t in r["traits"]:
            if t not in keys["traits"]:
                errs.append(f"race {r['key']} → unknown trait {t}")
        for s in r["subraces"]:
            if s not in keys["subraces"]:
                errs.append(f"race {r['key']} → unknown subrace {s}")
    for sr in comp["subraces"]:
        if sr["raceKey"] not in keys["races"]:
            errs.append(f"subrace {sr['key']} → unknown race {sr['raceKey']}")
    for cr in comp["creatures"]:
        for ci in cr["conditionImmunities"]:
            if ci not in keys["conditions"]:
                errs.append(f"creature {cr['key']} → unknown condition {ci}")
    for e in comp["equipment"]:
        if e.get("weapon"):
            for p in e["weapon"]["properties"]:
                if p not in keys["weapon_properties"]:
                    errs.append(f"equipment {e['key']} → unknown weapon property {p}")
    for sk in comp["skills"]:
        if sk["ability"] not in {"str", "dex", "con", "int", "wis", "cha"}:
            errs.append(f"skill {sk['key']} → bad ability {sk['ability']}")
    for ru in comp["rules"]:
        for s in ru["sections"]:
            if s not in keys["rule_sections"]:
                errs.append(f"rule {ru['key']} → unknown section {s}")
    return errs


def _hygiene(comp: dict) -> list[str]:
    errs = []
    blob = json.dumps(comp)
    m = URL_RE.search(blob)
    if m:
        errs.append(f"hygiene: an upstream API URL leaked into a non-xref field near …{blob[max(0, m.start()-60):m.start()+40]}…")
    # xref is allowed to hold the URL; check that only xref does.
    for kind, recs in comp.items():
        for r in recs:
            for k, v in r.items():
                if k == "xref":
                    continue
                if isinstance(v, str) and URL_RE.search(v):
                    errs.append(f"hygiene: {kind}/{r['key']}.{k} contains an API URL")
    for s in comp["spells"]:
        if not s["text"].strip():
            errs.append(f"hygiene: spell {s['key']} has empty text")
    for c in comp["conditions"]:
        if not c["text"].strip():
            errs.append(f"hygiene: condition {c['key']} has empty text")
    for f in comp["features"]:
        if not f["text"].strip():
            errs.append(f"hygiene: feature {f['key']} has empty text")
    return errs


def _slot_sanity(comp: dict) -> list[str]:
    """Spell slots never decrease with level and pact magic stays ≤ 4 slots."""
    errs = []
    for c in comp["classes"]:
        prev = [0] * 9
        pact = c["key"] == "warlock"  # Pact Magic: slots migrate upward in level (2×L1 → 2×L2 …)
        for lv in c["levels"]:
            if pact:
                if sum(lv["slots"]) < sum(prev) or sum(lv["slots"]) > 4:
                    errs.append(f"slots: {c['key']} L{lv['level']} pact slots {lv['slots']} implausible")
            else:
                for i, (a, b) in enumerate(zip(prev, lv["slots"])):
                    if b < a:
                        errs.append(f"slots: {c['key']} L{lv['level']} slot{i+1} decreased {a}→{b}")
            prev = lv["slots"]
            if lv["profBonus"] != 2 + (lv["level"] - 1) // 4:
                errs.append(f"prof: {c['key']} L{lv['level']} profBonus {lv['profBonus']} != table")
    return errs


def validate(comp: dict, expected_counts: dict[str, int]) -> None:
    errs: list[str] = []
    errs += _schema_errors(comp)
    errs += _count_errors(comp, expected_counts)
    errs += _unique_keys(comp)
    errs += _integrity(comp)
    errs += _hygiene(comp)
    errs += _slot_sanity(comp)
    # Skip the URL blob check false positives: xref fields legitimately hold URLs.
    errs = [e for e in errs if not e.startswith("hygiene: an upstream API URL leaked")]
    if errs:
        for e in errs:
            print(e, file=sys.stderr)
        raise ValidationError(f"{len(errs)} validation error(s)")
