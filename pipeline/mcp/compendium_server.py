"""`compendium` MCP server — gives Claude Code (and its subagents) tool-level access to
the bundled SRD 5.1 data and the Python reference rules engine.

Run (from the repo root):  python3 pipeline/mcp/compendium_server.py
Registered in .mcp.json as `compendium` (stdio).

Design notes
- Read-only over the committed assets in tool/src/main/assets/compendium/. If they are
  missing, every tool returns a clear "run `python3 -m pipeline build`" error.
- The rules tools wrap pipeline/reference/* so an agent can ask "what does this
  character's AC come to?" and get the oracle's answer, not a guess.
- `license_check` encodes docs/LICENSING.md's allowed-sources table so the licensing
  auditor agent has a deterministic answer for "may I ingest this?".
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))

from mcp.server.fastmcp import FastMCP  # noqa: E402

from pipeline.reference import dice, rules  # noqa: E402

ASSETS = ROOT / "tool" / "src" / "main" / "assets" / "compendium"
LEGAL = ROOT / "tool" / "src" / "main" / "assets" / "legal"

mcp = FastMCP("compendium", instructions=(
    "SRD 5.1 (2014 rules) compendium bundled in the Grimoire tool, plus the Python reference "
    "rules engine that the Kotlin implementation must match. Prefer these tools over memory "
    "for any rules text, table, or derived number."
))

_cache: dict[str, list[dict]] | None = None


def _load() -> dict[str, list[dict]]:
    global _cache
    if _cache is None:
        if not (ASSETS / "index.json").exists():
            raise RuntimeError("compendium assets not built — run `python3 -m pipeline build` from the repo root")
        _cache = {}
        for p in sorted(ASSETS.glob("*.json")):
            if p.name == "index.json":
                continue
            _cache[p.stem] = json.loads(p.read_text(encoding="utf-8"))
    return _cache


def _armor_table() -> dict[str, dict]:
    return {e["key"]: e["armor"] for e in _load()["equipment"] if e.get("armor")}


def _snippet(rec: dict, query: str, width: int = 160) -> str:
    text = rec.get("text") or rec.get("headline") or ""
    i = text.lower().find(query.lower())
    if i < 0:
        return text[:width]
    start = max(0, i - width // 3)
    return ("…" if start else "") + text[start:start + width] + ("…" if start + width < len(text) else "")


@mcp.tool()
def kinds() -> dict[str, int]:
    """List the compendium kinds (spells, creatures, classes, …) with record counts."""
    return {k: len(v) for k, v in _load().items()}


@mcp.tool()
def search(query: str, kind: str | None = None, limit: int = 10) -> list[dict]:
    """Search the compendium by name and text. `kind` narrows to one file (e.g. 'spells');
    results are ranked name-match first, then text-match. Returns kind/key/name/snippet."""
    data = _load()
    q = query.strip().lower()
    if not q:
        return []
    hits: list[tuple[int, dict]] = []
    for k, recs in data.items():
        if kind and k != kind:
            continue
        for r in recs:
            name = r["name"].lower()
            score = 0
            if name == q:
                score = 100
            elif name.startswith(q):
                score = 80
            elif q in name:
                score = 60
            elif q in (r.get("text") or "").lower() or q in (r.get("headline") or "").lower():
                score = 20
            if score:
                hits.append((score, {"kind": k, "key": r["key"], "name": r["name"], "snippet": _snippet(r, q)}))
    hits.sort(key=lambda h: (-h[0], h[1]["kind"], h[1]["name"]))
    return [h[1] for h in hits[: max(1, min(limit, 50))]]


@mcp.tool()
def get(kind: str, key: str) -> dict:
    """Fetch one full record, e.g. get('spells', 'fireball') or get('conditions', 'grappled')."""
    data = _load()
    if kind not in data:
        raise ValueError(f"unknown kind {kind!r}; one of {sorted(data)}")
    for r in data[kind]:
        if r["key"] == key:
            return r
    raise ValueError(f"no {kind} record with key {key!r}")


@mcp.tool()
def class_progression(class_key: str) -> dict:
    """Level 1–20 table for a class: proficiency bonus, features gained, spell slots,
    cantrips/spells known, class-specific counters (rage count, ki points, …)."""
    c = get("classes", class_key)
    feats = {f["key"]: f["name"] for f in _load()["features"]}
    return {
        "class": c["name"], "hitDie": c["hitDie"], "savingThrows": c["savingThrows"],
        "spellcasting": c.get("spellcasting"),
        "levels": [
            {**lv, "featureNames": [feats.get(k, k) for k in lv["features"]]} for lv in c["levels"]
        ],
    }


@mcp.tool()
def spell_slots(classes: list[dict]) -> dict:
    """Max spell slots for a class list like [{"classKey":"paladin","level":6},{"classKey":"warlock","level":2}].
    Returns {"slots":[L1..L9], "pact": {"count","level"}|null} per the 2014 rules (single-class table or the Multiclass Spellcaster rule)."""
    return rules.spell_slots(classes)


@mcp.tool()
def roll(expr: str, seed: int | None = None, mode: str = "none") -> dict:
    """Roll dice notation (e.g. '1d20+5', '8d6', '2d20kh1+3'). mode='adv'|'dis' rewrites a 1d20 term.
    With `seed`, the result is deterministic (mulberry32) — use it for test vectors."""
    e = dice.with_advantage(expr, mode)
    import secrets
    s = seed if seed is not None else secrets.randbits(32)
    r = dice.roll(e, s)
    return {"expression": r.expression, "seed": s, "rolls": r.rolls, "kept": r.kept, "total": r.total, "natural": r.natural,
            "min": dice.bounds(e)[0], "max": dice.bounds(e)[1], "average": dice.average(e)}


@mcp.tool()
def derive(character: dict) -> dict:
    """Derived sheet numbers for a character JSON (see pipeline/schema/character.schema.json):
    ability mods, prof bonus, saves, skills, passive perception, initiative, AC, current HP,
    spell DC/attack, max slots, attack to-hit/damage. This is the oracle the Kotlin code must match."""
    return rules.derive(character, _armor_table())


@mcp.tool()
def apply_events(character: dict, events: list[dict]) -> dict:
    """Apply in-play events to a character and return the new character JSON plus derived HP.
    Event types: damage{amount,critical?}, heal{amount}, temp{amount}, deathSave{d20},
    spendHitDie{die,roll}, shortRest, longRest, dawn, spendSlot{level}, spendPactSlot, counter{id,delta}."""
    end = rules.run(character, events)
    return {"character": end, "derivedHp": rules.derive(end, _armor_table())["hp"]}


_ALLOWED = {
    r"raw\.githubusercontent\.com/5e-bits/5e-database/": ("allowed", "SRD 5.1 text via 5e-bits (MIT scripts; CC-BY-4.0 content). Attribute with the SRD 5.1 sentence."),
    r"github\.com/5e-bits/": ("allowed", "SRD text; 2024 dataset is partial (no spells) — SRD 5.1 only for Grimoire v1."),
    r"github\.com/open5e/|api\.open5e\.com": ("allowed-with-caveat", "Only documents whose license is cc-by-40 (srd-2014, srd-2024, bfrd, a5e-*). Kobold OGL books (tob, ccdx, deepm…) are OGL-1.0a — do not ingest."),
    r"media\.wizards\.com/.*SRD|dndbeyond\.com/srd|SRD_CC_v5\.(1|2)": ("allowed", "The SRD PDFs themselves; CC-BY-4.0 with the prescribed attribution sentence."),
    r"koboldpress\.com/.*Black-Flag|bfrd\.net": ("allowed-optional", "Black Flag Reference Document — CC-BY-4.0 (and ORC); take it under CC-BY only. Not in v1 scope."),
    r"a5esrd\.com": ("allowed-optional", "A5E SRD — CC-BY-4.0 option. Not in v1 scope."),
    r"slyflourish\.com/lazy_gm_resource_document|github\.com/mshea/lazy_gm_tools": ("allowed-optional", "Lazy GM's Resource Document — CC-BY-4.0. GM-side; not in v1 scope."),
    r"5e\.tools|5etools|5etools-mirror": ("forbidden", "Verbatim WotC book text; MIT code license does not cover it; WotC DMCA'd mirrors (Aug 2024). Never ingest."),
    r"FightClub5eXML/.*Sources|kinkofer/FightClub5eXML": ("forbidden-as-data", "Community XML reproduces PHB text. Use only as an import *schema* reference, never as bundled data."),
    r"character-service\.dndbeyond\.com|dndbeyond\.com/(monsters|spells|magic-items|characters)": ("forbidden-as-data", "D&D Beyond content is proprietary; a player's OWN character JSON may be imported into their private store but never bundled."),
    r"foundryvtt\.com|github\.com/foundryvtt/dnd5e": ("allowed-with-caveat", "Pack *text* is SRD (CC-BY); token/art assets are NOT redistributable. Strip img/texture paths."),
}


@mcp.tool()
def license_check(source: str) -> dict:
    """Is this URL/repo an acceptable content source for the bundled compendium? Encodes
    docs/LICENSING.md. Returns verdict ∈ allowed | allowed-with-caveat | allowed-optional |
    forbidden | forbidden-as-data | unknown, with the reason."""
    for pattern, (verdict, reason) in _ALLOWED.items():
        if re.search(pattern, source, re.IGNORECASE):
            return {"source": source, "verdict": verdict, "reason": reason}
    return {"source": source, "verdict": "unknown", "reason": "Not in the allowed-sources table. Verify the license at the PRIMARY source and add a row to docs/LICENSING.md before ingesting."}


@mcp.tool()
def attribution() -> str:
    """The exact attribution text bundled with the data (assets/legal/ATTRIBUTION.md)."""
    p = LEGAL / "ATTRIBUTION.md"
    if not p.exists():
        raise RuntimeError("legal files not built — run `python3 -m pipeline build`")
    return p.read_text(encoding="utf-8")


@mcp.tool()
def bundle_info() -> dict[str, Any]:
    """index.json: edition, SRD version, bundle hash, per-file counts and sizes, pinned sources."""
    return json.loads((ASSETS / "index.json").read_text(encoding="utf-8"))


if __name__ == "__main__":
    mcp.run()
