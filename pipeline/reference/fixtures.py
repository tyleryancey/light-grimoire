"""Generate the golden fixtures the Kotlin tests replay.

Every fixture file is `{"$comment": …, "cases": [...]}` and byte-stable (sorted keys).
Regenerate with `python3 -m pipeline fixtures` after changing the reference engine; the
git diff IS the changelog of rule behaviour."""
from __future__ import annotations

import json
from pathlib import Path

from . import dice, rules

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
FIXTURES_DIR = REPO_ROOT / "fixtures"
CHARACTERS_DIR = FIXTURES_DIR / "characters"


def _dump(path: Path, obj) -> None:
    path.write_text(json.dumps(obj, indent=1, sort_keys=True, ensure_ascii=False) + "\n", encoding="utf-8")


def _load_armor(assets_dir: Path) -> dict[str, dict]:
    eq = json.loads((assets_dir / "equipment.json").read_text(encoding="utf-8"))
    return {e["key"]: e["armor"] for e in eq if e.get("armor")}


def gen_rng() -> dict:
    cases = []
    for seed in (0, 1, 42, 2024, 0xDEADBEEF, 0xFFFFFFFF):
        r = dice.Mulberry32(seed)
        first = [r.next_u32() for _ in range(8)]
        r = dice.Mulberry32(seed)
        d20 = [r.die(20) for _ in range(12)]
        r = dice.Mulberry32(seed)
        d6 = [r.die(6) for _ in range(12)]
        cases.append({"seed": seed, "u32": first, "d20": d20, "d6": d6})
    return {"$comment": "mulberry32 streams. u32 = raw outputs; d20/d6 = successive dice from a fresh stream with the same seed. Kotlin: Int arithmetic, ushr, and die = ((u32.toLong() and 0xFFFFFFFF) * sides ushr 32).toInt() + 1.", "cases": cases}


def gen_dice() -> dict:
    exprs = ["1d20", "d20+5", "2d20kh1+5", "2d20kl1+3", "1d8+3", "8d6", "2d6+1d4+2", "1d4-1", "-1d4+10", "4d6kh3", "1d100", "3d12+7", "1d20+0"]
    cases = []
    for e in exprs:
        for seed in (1, 7, 12345):
            r = dice.roll(e, seed)
            cases.append({
                "expr": e, "normalized": r.expression, "seed": seed, "rolls": r.rolls, "kept": r.kept,
                "total": r.total, "natural": r.natural, "min": dice.bounds(e)[0], "max": dice.bounds(e)[1],
                "average": dice.average(e),
            })
    invalid = ["", "++1", "1d7", "2d20kh3", "1d20+", "abc", "101d6", "0d6", "1d20kh0", "1 d 20 x"]
    adv = [{"expr": "1d20+5", "mode": "adv", "result": "2d20kh1+5"}, {"expr": "d20+2", "mode": "dis", "result": "2d20kl1+2"}, {"expr": "1d20-1", "mode": "none", "result": "1d20-1"}]
    crit = [{"expr": e, "result": dice.with_critical(e)} for e in ("1d8+3", "2d6+1d4+2", "1d6", "2d20kh1+5", "3d12-1")]
    pairs = []
    for seed in (1, 42, 999):
        rs = dice.roll_many(["1d20+5", "1d8+3"], seed)
        pairs.append({"seed": seed, "expressions": ["1d20+5", "1d8+3"], "rolls": [r.rolls for r in rs], "totals": [r.total for r in rs]})
    return {"$comment": "Roll each expr with Mulberry32(seed); dice are consumed left-to-right, term by term, in the order written. 'invalid' must be rejected. 'critical' doubles dice terms only. 'pairs' roll two expressions from ONE stream (the Turn screen's attack+damage tap).", "cases": cases, "invalid": invalid, "advantage": adv, "critical": crit, "pairs": pairs}


def gen_math() -> dict:
    return {
        "$comment": "Ability modifiers and proficiency bonus by level (SRD 5.1).",
        "abilityMod": [{"score": s, "mod": rules.ability_mod(s)} for s in range(1, 31)],
        "proficiencyBonus": [{"level": l, "bonus": rules.proficiency_bonus(l)} for l in range(1, 21)],
        "averageHitDie": rules.AVERAGE_HIT_DIE,
        "hpMaxAverage": [
            {"classes": [{"classKey": "wizard", "level": 1}], "con": 14, "hp": rules.hp_max_average([{"classKey": "wizard", "level": 1}], 14)},
            {"classes": [{"classKey": "fighter", "level": 5}], "con": 16, "hp": rules.hp_max_average([{"classKey": "fighter", "level": 5}], 16)},
            {"classes": [{"classKey": "barbarian", "level": 3}], "con": 6, "hp": rules.hp_max_average([{"classKey": "barbarian", "level": 3}], 6)},
            {"classes": [{"classKey": "paladin", "level": 6}, {"classKey": "warlock", "level": 2}], "con": 14, "hp": rules.hp_max_average([{"classKey": "paladin", "level": 6}, {"classKey": "warlock", "level": 2}], 14)},
            {"classes": [{"classKey": "sorcerer", "level": 20}], "con": 20, "hp": rules.hp_max_average([{"classKey": "sorcerer", "level": 20}], 20)},
        ],
    }


def gen_slots() -> dict:
    single = []
    for cls in sorted(rules.CASTER_TYPE):
        for lvl in range(1, 21):
            single.append({"classes": [{"classKey": cls, "level": lvl}], **rules.spell_slots([{"classKey": cls, "level": lvl}])})
    multi_cases = [
        [{"classKey": "paladin", "level": 6}, {"classKey": "warlock", "level": 2}],
        [{"classKey": "paladin", "level": 3}, {"classKey": "fighter", "level": 1}],
        [{"classKey": "paladin", "level": 3}, {"classKey": "wizard", "level": 1}],
        [{"classKey": "cleric", "level": 1}, {"classKey": "warlock", "level": 3}],
        [{"classKey": "wizard", "level": 5}, {"classKey": "cleric", "level": 3}],
        [{"classKey": "ranger", "level": 5}, {"classKey": "rogue", "level": 3}],
        [{"classKey": "sorcerer", "level": 17}, {"classKey": "warlock", "level": 3}],
        [{"classKey": "fighter", "level": 4}, {"classKey": "rogue", "level": 4}],
        [{"classKey": "bard", "level": 1}, {"classKey": "paladin", "level": 1}],
        [{"classKey": "custom-ek", "level": 7, "custom": {"casterType": "third", "hitDie": 10}}],
        [{"classKey": "custom-ek", "level": 6, "custom": {"casterType": "third", "hitDie": 10}}, {"classKey": "wizard", "level": 2}],
    ]
    multi = [{"classes": c, **rules.spell_slots(c)} for c in multi_cases]
    return {"$comment": "slots = [L1..L9] max slots; pact = Pact Magic {count, level} or null. Spellcasting from exactly one class (even when multiclassed with non-casters or a warlock) uses that class's table; Spellcasting from two or more classes uses the SRD Multiclass Spellcaster rule.", "single": single, "multiclass": multi}


def gen_derived(armor: dict[str, dict]) -> dict:
    cases = []
    for path in sorted(CHARACTERS_DIR.glob("*.json")):
        ch = json.loads(path.read_text(encoding="utf-8"))
        cases.append({"character": path.name, "derived": rules.derive(ch, armor)})
    return {"$comment": "derive(character) for each fixtures/characters/*.json. AC uses the compendium armor table (base, dexBonus, maxBonus).", "cases": cases}


def gen_events(armor: dict[str, dict]) -> dict:
    cleric = json.loads((CHARACTERS_DIR / "cleric-5-life.json").read_text(encoding="utf-8"))
    rogue = json.loads((CHARACTERS_DIR / "rogue-3-thief.json").read_text(encoding="utf-8"))
    pal = json.loads((CHARACTERS_DIR / "paladin-6-warlock-2.json").read_text(encoding="utf-8"))

    def scen(name, start, events):
        end = rules.run(start, events)
        return {"name": name, "start": start["id"], "events": events, "end": {
            "hp": end["hp"], "deathSaves": end.get("deathSaves"), "hitDice": end.get("hitDice"),
            "counters": end.get("counters"), "spellcasting": {k: v for k, v in (end.get("spellcasting") or {}).items() if k in ("slotsUsed", "pactUsed")} if end.get("spellcasting") else None,
            "exhaustion": end.get("exhaustion"), "conditions": end.get("conditions"),
            "derivedHp": rules.derive(end, armor)["hp"],
        }}

    scenarios = [
        scen("damage then heal", cleric, [{"type": "damage", "amount": 10}, {"type": "heal", "amount": 4}]),
        scen("temp hp absorbs first", rogue, [{"type": "damage", "amount": 3}, {"type": "damage", "amount": 7}]),
        scen("temp hp does not stack (keep higher)", rogue, [{"type": "temp", "amount": 3}, {"type": "temp", "amount": 8}]),
        scen("overheal caps at max", cleric, [{"type": "heal", "amount": 999}]),
        scen("drop to zero resets death saves", cleric, [{"type": "damage", "amount": 31}]),
        scen("instant death from massive overflow", rogue, [{"type": "damage", "amount": 5 + 24 + 24}]),
        scen("damage at zero = failure; crit = two", pal, [{"type": "damage", "amount": 1}, {"type": "damage", "amount": 1, "critical": True}]),
        scen("temp hp absorbs damage while at zero (no failure)", pal, [{"type": "temp", "amount": 6}, {"type": "damage", "amount": 5}, {"type": "damage", "amount": 3}]),
        scen("death save sequence: 12, 9, 20 → back to 1 hp", pal, [{"type": "deathSave", "d20": 12}, {"type": "deathSave", "d20": 9}, {"type": "deathSave", "d20": 20}]),
        scen("death save nat 1 counts two", pal, [{"type": "deathSave", "d20": 1}]),
        scen("three successes → stable", pal, [{"type": "deathSave", "d20": 15}, {"type": "deathSave", "d20": 15}]),
        scen("three failures → dead", pal, [{"type": "deathSave", "d20": 3}, {"type": "deathSave", "d20": 3}]),
        scen("healing at zero revives and clears saves", pal, [{"type": "heal", "amount": 7}]),
        scen("short rest: hit die + con, counters reset, pact reset", pal, [{"type": "heal", "amount": 1}, {"type": "spendHitDie", "die": 10, "roll": 6}, {"type": "shortRest"}]),
        scen("hit die never heals negative", rogue, [{"type": "damage", "amount": 10}, {"type": "spendHitDie", "die": 8, "roll": 1}]),
        scen("long rest: full hp, half hit dice back (largest first), slots, exhaustion -1", pal, [{"type": "heal", "amount": 1}, {"type": "longRest"}]),
        scen("long rest clears temp hp and reduces exhaustion", rogue, [{"type": "damage", "amount": 2}, {"type": "longRest"}]),
        scen("long rest on cleric resets short+long counters and slots", cleric, [{"type": "longRest"}]),
        scen("spend slots then long rest", cleric, [{"type": "spendSlot", "level": 3}, {"type": "spendSlot", "level": 3}, {"type": "longRest"}]),
        scen("counter clamps at 0 and max", cleric, [{"type": "counter", "id": "channel-divinity", "delta": -1}, {"type": "counter", "id": "channel-divinity", "delta": 5}]),
        scen("dawn resets only dawn counters", cleric, [{"type": "dawn"}]),
    ]
    errors = [
        {"start": "cleric-5-life", "events": [{"type": "spendSlot", "level": 3}, {"type": "spendSlot", "level": 3}, {"type": "spendSlot", "level": 3}], "error": "no level-3 slots left"},
        {"start": "rogue-3-thief", "events": [{"type": "spendPactSlot"}], "error": "no pact magic"},
        {"start": "paladin-6-warlock-2", "events": [{"type": "spendHitDie", "die": 8, "roll": 4}], "error": "no d8 hit dice left"},
    ]
    return {"$comment": "Replay events on the named fixtures/characters/*.json and compare the end state. 'errors' must raise (the UI should have disabled the control).", "scenarios": scenarios, "errors": errors}


def write_all(assets_dir: Path) -> list[Path]:
    FIXTURES_DIR.mkdir(parents=True, exist_ok=True)
    armor = _load_armor(assets_dir)
    out = {
        "rng.json": gen_rng(),
        "dice.json": gen_dice(),
        "math.json": gen_math(),
        "slots.json": gen_slots(),
        "derived.json": gen_derived(armor),
        "events.json": gen_events(armor),
    }
    written = []
    for name, obj in out.items():
        p = FIXTURES_DIR / name
        _dump(p, obj)
        written.append(p)
    return written
