"""Reference-engine tests. Run: `python3 -m pytest pipeline/tests -q` from the repo root."""
from __future__ import annotations

import json
from pathlib import Path

import pytest

from pipeline.reference import dice, rules

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "tool" / "src" / "main" / "assets" / "compendium"


# ---------------------------------------------------------------------------- dice

def test_mulberry32_matches_js_reference():
    # Values produced by the canonical JS mulberry32 (see dice.py docstring) in Node.
    r = dice.Mulberry32(1)
    assert [r.next_u32() for _ in range(3)] == [2693262067, 11749833, 2265367787]
    r = dice.Mulberry32(0xFFFFFFFF)
    assert [r.next_u32(), r.next_u32()] == [3850105811, 813802916]
    r = dice.Mulberry32(1)
    assert [r.die(20) for _ in range(10)] == [13, 1, 11, 20, 20, 6, 13, 15, 9, 20]


def test_die_is_uniform_enough():
    r = dice.Mulberry32(12345)
    counts = [0] * 6
    for _ in range(60000):
        counts[r.die(6) - 1] += 1
    assert all(9500 < c < 10500 for c in counts)


@pytest.mark.parametrize("expr,lo,hi", [("1d20", 1, 20), ("2d20kh1+5", 6, 25), ("8d6", 8, 48), ("1d4-1", 0, 3), ("-1d4+10", 6, 9), ("4d6kh3", 3, 18)])
def test_bounds(expr, lo, hi):
    assert dice.bounds(expr) == (lo, hi)


@pytest.mark.parametrize("bad", ["", "++1", "1d7", "2d20kh3", "1d20+", "abc", "101d6", "0d6", "1d20kh0", "1 d 20 x"])
def test_invalid_rejected(bad):
    with pytest.raises(dice.DiceError):
        dice.parse(bad)


def test_advantage_rewrite():
    assert dice.with_advantage("1d20+5", "adv") == "2d20kh1+5"
    assert dice.with_advantage("d20+2", "dis") == "2d20kl1+2"
    assert dice.with_advantage("1d20-1", "none") == "1d20-1"
    with pytest.raises(dice.DiceError):
        dice.with_advantage("2d6", "adv")


def test_roll_consumes_dice_in_order():
    r = dice.roll("1d20+5", 42)
    assert r.rolls == [[13]] and r.total == 18 and r.natural == 13
    r = dice.roll("2d20kh1+5", 42)
    assert r.rolls == [[13, 9]] and r.kept == [[13]] and r.total == 18


# --------------------------------------------------------------------------- basics

@pytest.mark.parametrize("score,mod", [(1, -5), (8, -1), (9, -1), (10, 0), (11, 0), (12, 1), (15, 2), (20, 5), (30, 10)])
def test_ability_mod(score, mod):
    assert rules.ability_mod(score) == mod


@pytest.mark.parametrize("level,bonus", [(1, 2), (4, 2), (5, 3), (8, 3), (9, 4), (12, 4), (13, 5), (16, 5), (17, 6), (20, 6)])
def test_prof_bonus(level, bonus):
    assert rules.proficiency_bonus(level) == bonus


def test_hp_max_average():
    assert rules.hp_max_average([{"classKey": "wizard", "level": 1}], 14) == 8
    assert rules.hp_max_average([{"classKey": "fighter", "level": 5}], 16) == 10 + 3 + 4 * (6 + 3)
    # CON 6 (-2) with a d12: level 1 = 10, later levels = 7-2 = 5 each
    assert rules.hp_max_average([{"classKey": "barbarian", "level": 3}], 6) == 10 + 5 + 5
    # minimum 1 per level with a terrible CON on a d6
    assert rules.hp_max_average([{"classKey": "wizard", "level": 3}], 1) == max(1, 6 - 5) + 2 * max(1, 4 - 5)


# ---------------------------------------------------------- oracle vs compendium data

@pytest.mark.skipif(not (ASSETS / "classes.json").exists(), reason="run `python3 -m pipeline build` first")
def test_single_class_slots_match_srd_tables():
    classes = json.loads((ASSETS / "classes.json").read_text(encoding="utf-8"))
    for c in classes:
        for row in c["levels"]:
            got = rules.spell_slots([{"classKey": c["key"], "level": row["level"]}])
            if c["key"] == "warlock":
                # SRD Levels data lists pact slots in the slot-level column.
                pact = got["pact"]
                expected = [0] * 9
                expected[pact["level"] - 1] = pact["count"]
                assert row["slots"] == expected, (c["key"], row["level"])
                assert got["slots"] == [0] * 9
            else:
                assert got["slots"] == row["slots"], (c["key"], row["level"], got["slots"], row["slots"])
            assert rules.proficiency_bonus(row["level"]) == row["profBonus"]


@pytest.mark.skipif(not (ASSETS / "classes.json").exists(), reason="run `python3 -m pipeline build` first")
def test_hit_dice_match_compendium():
    classes = json.loads((ASSETS / "classes.json").read_text(encoding="utf-8"))
    for c in classes:
        assert rules.HIT_DIE[c["key"]] == c["hitDie"]
        sc = c.get("spellcasting")
        if sc:
            assert rules.SPELLCASTING_ABILITY[c["key"]] == sc["ability"]
        else:
            assert c["key"] not in rules.SPELLCASTING_ABILITY


@pytest.mark.skipif(not (ASSETS / "skills.json").exists(), reason="run `python3 -m pipeline build` first")
def test_skill_abilities_match_compendium():
    skills = json.loads((ASSETS / "skills.json").read_text(encoding="utf-8"))
    assert {s["key"]: s["ability"] for s in skills} == rules.SKILLS


def test_multiclass_slots():
    assert rules.spell_slots([{"classKey": "paladin", "level": 6}, {"classKey": "warlock", "level": 2}]) == {"slots": [4, 2, 0, 0, 0, 0, 0, 0, 0], "pact": {"count": 2, "level": 1}}
    # Spellcasting from one class keeps that class's table even when multiclassed with a
    # non-caster; two Spellcasting classes switch to the multiclass rule (Paladin 3 → caster
    # level 1, + Wizard 1 → 2 → three 1st-level slots).
    assert rules.spell_slots([{"classKey": "paladin", "level": 3}])["slots"][0] == 3
    assert rules.spell_slots([{"classKey": "paladin", "level": 3}, {"classKey": "fighter", "level": 1}])["slots"][0] == 3
    assert rules.spell_slots([{"classKey": "paladin", "level": 3}, {"classKey": "wizard", "level": 1}])["slots"] == [3, 0, 0, 0, 0, 0, 0, 0, 0]
    assert rules.spell_slots([{"classKey": "paladin", "level": 5}, {"classKey": "cleric", "level": 1}])["slots"] == [4, 2, 0, 0, 0, 0, 0, 0, 0]
    assert rules.spell_slots([{"classKey": "cleric", "level": 1}, {"classKey": "warlock", "level": 3}]) == {"slots": [2, 0, 0, 0, 0, 0, 0, 0, 0], "pact": {"count": 2, "level": 2}}
    assert rules.spell_slots([{"classKey": "wizard", "level": 5}, {"classKey": "cleric", "level": 3}])["slots"] == rules.FULL_CASTER_SLOTS[7]
    assert rules.spell_slots([{"classKey": "fighter", "level": 4}, {"classKey": "rogue", "level": 4}]) == {"slots": [0] * 9, "pact": None}


# ------------------------------------------------------------------ hit point ledger

def _pc(max_hp=20, damage=0, temp=0, con=10):
    return {"id": "t", "classes": [{"classKey": "fighter", "level": 2}], "abilities": {"str": 10, "dex": 10, "con": con, "int": 10, "wis": 10, "cha": 10},
            "hp": {"max": max_hp, "damage": damage, "temp": temp}, "hitDice": [{"die": 10, "total": 2, "used": 0}], "counters": [], "exhaustion": 0}


def test_temp_hp_absorbs_first():
    ch = rules.apply_damage(_pc(temp=5), 3)
    assert ch["hp"] == {"max": 20, "damage": 0, "temp": 2}
    ch = rules.apply_damage(ch, 7)
    assert ch["hp"] == {"max": 20, "damage": 5, "temp": 0}


def test_temp_hp_absorbs_at_zero_hp():
    ch = rules.apply_damage(_pc(), 20)                # down
    ch = rules.apply_temp_hp(ch, 6)
    ch = rules.apply_damage(ch, 5)                     # fully absorbed → no failure
    assert ch["deathSaves"]["failures"] == 0 and ch["hp"]["temp"] == 1
    ch = rules.apply_damage(ch, 3)                     # 1 absorbed, 2 through → one failure
    assert ch["deathSaves"]["failures"] == 1 and ch["hp"]["temp"] == 0


def test_critical_rewrite_doubles_dice_only():
    assert dice.with_critical("1d8+3") == "2d8+3"
    assert dice.with_critical("2d6+1d4+2") == "4d6+2d4+2"
    r = dice.roll_many(["1d20+5", "1d8+3"], 42)
    assert [x.total for x in r] == [18, 7]


def test_instant_death_only_on_overflow_ge_max():
    ch = rules.apply_damage(_pc(damage=15), 5 + 19)   # overflow 19 < 20
    assert ch["deathSaves"]["dead"] is False and ch["hp"]["damage"] == 20
    ch = rules.apply_damage(_pc(damage=15), 5 + 20)   # overflow 20 == max
    assert ch["deathSaves"]["dead"] is True


def test_death_saves():
    ch = rules.apply_damage(_pc(), 20)
    ch = rules.death_save(ch, 10)
    ch = rules.death_save(ch, 9)
    assert ch["deathSaves"] == {"successes": 1, "failures": 1, "stable": False, "dead": False}
    ch = rules.death_save(ch, 20)
    assert ch["hp"]["damage"] == 19 and ch["deathSaves"]["successes"] == 0
    ch2 = rules.death_save(rules.apply_damage(_pc(), 20), 1)
    assert ch2["deathSaves"]["failures"] == 2


def test_long_rest_hit_dice_half_minimum_one():
    ch = _pc()
    ch["hitDice"] = [{"die": 10, "total": 1, "used": 1}]
    ch = rules.long_rest(ch)
    assert ch["hitDice"][0]["used"] == 0
    ch = _pc()
    ch["hitDice"] = [{"die": 10, "total": 7, "used": 7}, {"die": 6, "total": 2, "used": 2}]
    ch = rules.long_rest(ch)
    assert ch["hitDice"] == [{"die": 10, "total": 7, "used": 3}, {"die": 6, "total": 2, "used": 2}]  # 9 total → regain 4, largest first


def test_short_rest_resets_only_short_counters():
    ch = _pc()
    ch["counters"] = [{"id": "a", "value": 0, "max": 2, "reset": "short"}, {"id": "b", "value": 0, "max": 2, "reset": "long"}, {"id": "c", "value": 0, "max": 2, "reset": "dawn"}]
    ch = rules.short_rest(ch)
    assert [c["value"] for c in ch["counters"]] == [2, 0, 0]
    ch = rules.long_rest(ch)
    assert [c["value"] for c in ch["counters"]] == [2, 2, 0]
    ch = rules.dawn(ch)
    assert [c["value"] for c in ch["counters"]] == [2, 2, 2]


# ------------------------------------------------------------- fixtures are current

def test_fixtures_are_regenerated():
    """`python3 -m pipeline fixtures` output must be committed; drift = stale oracle."""
    from pipeline.reference import fixtures as fx

    if not ASSETS.exists():
        pytest.skip("build assets first")
    # derived.json and events.json need the compendium armor table, exactly as write_all() loads it;
    # without them here, drift in derive()/run() would surface only in the Kotlin replay.
    armor = fx._load_armor(ASSETS)
    generators = (
        ("rng.json", fx.gen_rng),
        ("dice.json", fx.gen_dice),
        ("math.json", fx.gen_math),
        ("slots.json", fx.gen_slots),
        ("derived.json", lambda: fx.gen_derived(armor)),
        ("events.json", lambda: fx.gen_events(armor)),
    )
    for name, gen in generators:
        on_disk = json.loads((ROOT / "fixtures" / name).read_text(encoding="utf-8"))
        assert on_disk == json.loads(json.dumps(gen())), f"{name} is stale — run python3 -m pipeline fixtures"
