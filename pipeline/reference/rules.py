"""2014-rules (SRD 5.1) reference engine: derived statistics, hit-point ledger, rests,
death saves, spell slots. Pure functions over plain dicts so fixtures serialize cleanly.

Character JSON shape: see pipeline/schema/character.schema.json (mirrors the Kotlin
`Character` model). Only the fields needed for math are read here.
"""
from __future__ import annotations

from copy import deepcopy
from typing import Any

ABILITIES = ["str", "dex", "con", "int", "wis", "cha"]

# SRD 5.1 skills → governing ability (Player's Handbook ch. 7 / SRD "Using Ability Scores").
SKILLS: dict[str, str] = {
    "acrobatics": "dex", "animal-handling": "wis", "arcana": "int", "athletics": "str",
    "deception": "cha", "history": "int", "insight": "wis", "intimidation": "cha",
    "investigation": "int", "medicine": "wis", "nature": "int", "perception": "wis",
    "performance": "cha", "persuasion": "cha", "religion": "int", "sleight-of-hand": "dex",
    "stealth": "dex", "survival": "wis",
}

# Caster progression for the SRD 5.1 classes. "third" is kept for custom subclasses
# (Eldritch Knight / Arcane Trickster are not in the SRD).
CASTER_TYPE: dict[str, str] = {
    "bard": "full", "cleric": "full", "druid": "full", "sorcerer": "full", "wizard": "full",
    "paladin": "half", "ranger": "half",
    "warlock": "pact",
    "barbarian": "none", "fighter": "none", "monk": "none", "rogue": "none",
}

SPELLCASTING_ABILITY: dict[str, str] = {
    "bard": "cha", "cleric": "wis", "druid": "wis", "paladin": "cha", "ranger": "wis",
    "sorcerer": "cha", "warlock": "cha", "wizard": "int",
}

HIT_DIE: dict[str, int] = {
    "barbarian": 12, "fighter": 10, "paladin": 10, "ranger": 10,
    "bard": 8, "cleric": 8, "druid": 8, "monk": 8, "rogue": 8, "warlock": 8,
    "sorcerer": 6, "wizard": 6,
}

# Spell Slots per Spell Level, by caster level 1..20 (SRD 5.1 "The Multiclass Spellcaster"
# table, identical to the full-caster class tables).
FULL_CASTER_SLOTS: list[list[int]] = [
    [2, 0, 0, 0, 0, 0, 0, 0, 0],
    [3, 0, 0, 0, 0, 0, 0, 0, 0],
    [4, 2, 0, 0, 0, 0, 0, 0, 0],
    [4, 3, 0, 0, 0, 0, 0, 0, 0],
    [4, 3, 2, 0, 0, 0, 0, 0, 0],
    [4, 3, 3, 0, 0, 0, 0, 0, 0],
    [4, 3, 3, 1, 0, 0, 0, 0, 0],
    [4, 3, 3, 2, 0, 0, 0, 0, 0],
    [4, 3, 3, 3, 1, 0, 0, 0, 0],
    [4, 3, 3, 3, 2, 0, 0, 0, 0],
    [4, 3, 3, 3, 2, 1, 0, 0, 0],
    [4, 3, 3, 3, 2, 1, 0, 0, 0],
    [4, 3, 3, 3, 2, 1, 1, 0, 0],
    [4, 3, 3, 3, 2, 1, 1, 0, 0],
    [4, 3, 3, 3, 2, 1, 1, 1, 0],
    [4, 3, 3, 3, 2, 1, 1, 1, 0],
    [4, 3, 3, 3, 2, 1, 1, 1, 1],
    [4, 3, 3, 3, 3, 1, 1, 1, 1],
    [4, 3, 3, 3, 3, 2, 1, 1, 1],
    [4, 3, 3, 3, 3, 2, 2, 1, 1],
]

# Warlock Pact Magic: (slot count, slot level) by warlock level 1..20.
PACT_SLOTS: list[tuple[int, int]] = [
    (1, 1), (2, 1), (2, 2), (2, 2), (2, 3), (2, 3), (2, 4), (2, 4), (2, 5), (2, 5),
    (3, 5), (3, 5), (3, 5), (3, 5), (3, 5), (3, 5), (4, 5), (4, 5), (4, 5), (4, 5),
]


# ----------------------------------------------------------------------------- basics

def ability_mod(score: int) -> int:
    return (score - 10) // 2  # Python floor division == floor((score-10)/2) for negatives too


def proficiency_bonus(level: int) -> int:
    if not 1 <= level <= 20:
        raise ValueError("level must be 1..20")
    return 2 + (level - 1) // 4


def total_level(character: dict) -> int:
    return sum(c["level"] for c in character["classes"])


def hit_die_for(class_key: str, custom: dict | None) -> int:
    if class_key in HIT_DIE:
        return HIT_DIE[class_key]
    if custom and "hitDie" in custom:
        return int(custom["hitDie"])
    raise ValueError(f"unknown class {class_key!r}: custom classes must declare hitDie")


# ------------------------------------------------------------------------ spell slots

def caster_type(class_key: str, custom: dict | None = None) -> str:
    if class_key in CASTER_TYPE:
        return CASTER_TYPE[class_key]
    return (custom or {}).get("casterType", "none")


def spell_slots(classes: list[dict]) -> dict[str, Any]:
    """Return {"slots": [9 ints], "pact": {"count": n, "level": L} | None}.

    SRD 5.1 Multiclassing → Spellcasting: "If you multiclass but have the Spellcasting
    feature from only one class, you follow the rules as described in that class. Once you
    have the Spellcasting feature from more than one class, use the rules below": caster
    level = full levels + floor(half/2) + floor(third/3) on the Multiclass Spellcaster
    table. Pact Magic is not the Spellcasting feature, is tracked separately, and never
    combines. Note the single-class half-caster tables (ceil(L/2)) differ from the
    multiclass floor rule at odd levels — a Paladin 3 / Wizard 1 has caster level 2.
    """
    if not classes:
        return {"slots": [0] * 9, "pact": None}
    pact = None
    for c in classes:
        if caster_type(c["classKey"], c.get("custom")) == "pact":
            count, level = PACT_SLOTS[int(c["level"]) - 1]
            pact = {"count": count, "level": level}
    slot_classes = [c for c in classes if caster_type(c["classKey"], c.get("custom")) in ("full", "half", "third")]
    if not slot_classes:
        return {"slots": [0] * 9, "pact": pact}
    if len(slot_classes) == 1:
        # Spellcasting from ONE class (single-class, or multiclassed with non-casters /
        # a warlock): that class's own table. Half casters = full table at ceil(L/2) from
        # level 2; third casters = full table at ceil(L/3) from level 3 (matches the SRD
        # Paladin/Ranger tables exactly — verified against the compendium in tests).
        c = slot_classes[0]
        kind = caster_type(c["classKey"], c.get("custom"))
        lvl = int(c["level"])
        if kind == "full":
            eff = lvl
        elif kind == "half":
            eff = 0 if lvl < 2 else (lvl + 1) // 2
        else:
            eff = 0 if lvl < 3 else (lvl + 2) // 3
    else:
        # Spellcasting from two or more classes: the Multiclass Spellcaster rule,
        # full + floor(half/2) + floor(third/3).
        eff = 0
        for c in slot_classes:
            kind = caster_type(c["classKey"], c.get("custom"))
            lvl = int(c["level"])
            eff += lvl if kind == "full" else lvl // 2 if kind == "half" else lvl // 3
    slots = list(FULL_CASTER_SLOTS[eff - 1]) if eff > 0 else [0] * 9
    return {"slots": slots, "pact": pact}


# ----------------------------------------------------------------------- derived stats

def _skill_multiplier(level: str) -> float:
    return {"none": 0.0, "half": 0.5, "proficient": 1.0, "expertise": 2.0}[level]


def armor_class(character: dict, mods: dict[str, int], armor_table: dict[str, dict] | None = None) -> int:
    """Compute AC from the `ac` block. `mode: manual` returns `value` untouched.

    Computed modes (2014):
      unarmored            10 + DEX
      armor <key>          base + DEX (light) / base + min(DEX, 2) (medium) / base (heavy)
      unarmored-monk       10 + DEX + WIS   (no armor, no shield)
      unarmored-barbarian  10 + DEX + CON   (no armor; shield allowed)
      mage-armor           13 + DEX
    plus +2 for `shield`, plus `bonus` (rings, cloaks, fighting style…).
    """
    ac = character.get("ac") or {"mode": "manual", "value": 10}
    if ac.get("mode", "manual") == "manual":
        return int(ac["value"])
    dex = mods["dex"]
    formula = ac.get("formula", "unarmored")
    if formula == "unarmored":
        base = 10 + dex
    elif formula == "unarmored-monk":
        base = 10 + dex + mods["wis"]
    elif formula == "unarmored-barbarian":
        base = 10 + dex + mods["con"]
    elif formula == "mage-armor":
        base = 13 + dex
    elif formula == "armor":
        key = ac.get("armorKey")
        table = armor_table or {}
        if key not in table:
            raise ValueError(f"unknown armor {key!r}")
        arm = table[key]
        if not arm.get("dexBonus", False):
            base = arm["base"]
        elif arm.get("maxBonus") is not None:
            base = arm["base"] + min(dex, arm["maxBonus"])
        else:
            base = arm["base"] + dex
    else:
        raise ValueError(f"unknown ac formula {formula!r}")
    if ac.get("shield"):
        base += 2
    return base + int(ac.get("bonus", 0))


def derive(character: dict, armor_table: dict[str, dict] | None = None) -> dict[str, Any]:
    """Everything the sheet shows that is not stored."""
    scores = character["abilities"]
    mods = {a: ability_mod(int(scores[a])) for a in ABILITIES}
    level = total_level(character)
    prof = character.get("profBonusOverride") or proficiency_bonus(level)
    save_profs = set(character.get("saveProficiencies", []))
    saves = {a: mods[a] + (prof if a in save_profs else 0) for a in ABILITIES}
    skill_levels = character.get("skills", {})
    skills = {}
    for sk, ab in SKILLS.items():
        mult = _skill_multiplier(skill_levels.get(sk, "none"))
        skills[sk] = mods[ab] + int(prof * mult)  # half-proficiency rounds down
    passive_perception = 10 + skills["perception"]
    initiative = mods["dex"] + int(character.get("initiativeBonus", 0))

    hp = character["hp"]
    current = max(0, int(hp["max"]) - int(hp.get("damage", 0)))
    temp = int(hp.get("temp", 0))
    bloodied = current * 2 <= int(hp["max"]) and current > 0

    # Spellcasting: ability from the character (explicit) or the primary spellcasting class.
    sc = character.get("spellcasting") or {}
    cast_ability = sc.get("ability")
    if not cast_ability:
        for c in character["classes"]:
            if c["classKey"] in SPELLCASTING_ABILITY:
                cast_ability = SPELLCASTING_ABILITY[c["classKey"]]
                break
    spell_dc = 8 + prof + mods[cast_ability] if cast_ability else None
    spell_attack = prof + mods[cast_ability] if cast_ability else None
    slots = spell_slots(character["classes"])

    attacks = []
    for atk in character.get("attacks", []):
        ab = atk.get("ability", "str")
        to_hit = atk["bonusOverride"] if atk.get("bonusOverride") is not None else mods[ab] + (prof if atk.get("proficient", True) else 0) + int(atk.get("bonus", 0))
        dmg_bonus = mods[ab] if atk.get("damageBonusMode", "ability") == "ability" else 0
        dmg_bonus += int(atk.get("damageBonus", 0))
        formula = atk["damage"] + (f"+{dmg_bonus}" if dmg_bonus > 0 else (f"{dmg_bonus}" if dmg_bonus < 0 else ""))
        attacks.append({"id": atk["id"], "name": atk["name"], "toHit": to_hit, "damage": formula, "damageType": atk.get("damageType")})

    return {
        "level": level,
        "profBonus": prof,
        "abilityMods": mods,
        "saves": saves,
        "skills": skills,
        "passivePerception": passive_perception,
        "initiative": initiative,
        "ac": armor_class(character, mods, armor_table),
        "hp": {"current": current, "max": int(hp["max"]), "temp": temp, "bloodied": bloodied, "down": current == 0},
        "spellcasting": {"ability": cast_ability, "saveDc": spell_dc, "attackBonus": spell_attack, "slotsMax": slots["slots"], "pact": slots["pact"]},
        "attacks": attacks,
    }


# ------------------------------------------------------------------- hit point ledger

def _reset_death_saves(ch: dict) -> None:
    ch["deathSaves"] = {"successes": 0, "failures": 0, "stable": False, "dead": False}


def apply_damage(ch: dict, amount: int, critical: bool = False) -> dict:
    """Temp HP absorb first; damage at 0 HP is a death-save failure (two on a crit);
    damage ≥ max HP remaining after reaching 0 is instant death (SRD: Instant Death)."""
    ch = deepcopy(ch)
    if amount < 0:
        raise ValueError("damage must be ≥ 0")
    hp = ch["hp"]
    ds = ch.setdefault("deathSaves", {"successes": 0, "failures": 0, "stable": False, "dead": False})
    current = int(hp["max"]) - int(hp.get("damage", 0))
    temp = int(hp.get("temp", 0))
    # Temporary hit points absorb first — also while at 0 HP ("They can still absorb
    # damage directed at you while you're in that state", SRD Damage and Healing).
    absorbed = min(temp, amount)
    hp["temp"] = temp - absorbed
    remaining = amount - absorbed
    if current <= 0:
        if remaining <= 0:
            return ch  # fully absorbed: no failure
        # Already at 0: no HP change, but a failure (or two); massive damage kills outright.
        ds["failures"] = min(3, ds["failures"] + (2 if critical else 1))
        ds["stable"] = False
        if remaining >= int(hp["max"]):
            ds["dead"] = True
        if ds["failures"] >= 3:
            ds["dead"] = True
        return ch
    new_current = current - remaining
    if new_current <= 0:
        overflow = -new_current
        hp["damage"] = int(hp["max"])
        _reset_death_saves(ch)
        if overflow >= int(hp["max"]):
            ch["deathSaves"]["dead"] = True  # Instant Death
    else:
        hp["damage"] = int(hp.get("damage", 0)) + remaining
    return ch


def apply_healing(ch: dict, amount: int) -> dict:
    ch = deepcopy(ch)
    if amount < 0:
        raise ValueError("healing must be ≥ 0")
    hp = ch["hp"]
    if ch.get("deathSaves", {}).get("dead"):
        return ch  # healing does not raise the dead
    hp["damage"] = max(0, int(hp.get("damage", 0)) - amount)
    if int(hp["max"]) - hp["damage"] > 0:
        _reset_death_saves(ch)  # regaining any HP ends death saves
    return ch


def apply_temp_hp(ch: dict, amount: int) -> dict:
    """Temp HP don't stack: keep the higher of existing and new (the player's choice
    is always to keep the higher, so the tool does that)."""
    ch = deepcopy(ch)
    ch["hp"]["temp"] = max(int(ch["hp"].get("temp", 0)), int(amount))
    return ch


def death_save(ch: dict, d20: int) -> dict:
    """Roll a death saving throw with a given natural d20 (1..20)."""
    ch = deepcopy(ch)
    ds = ch.setdefault("deathSaves", {"successes": 0, "failures": 0, "stable": False, "dead": False})
    if ds.get("dead") or ds.get("stable"):
        return ch
    if d20 == 20:
        ch["hp"]["damage"] = int(ch["hp"]["max"]) - 1  # regain 1 HP
        _reset_death_saves(ch)
    elif d20 == 1:
        ds["failures"] = min(3, ds["failures"] + 2)
    elif d20 >= 10:
        ds["successes"] += 1
    else:
        ds["failures"] += 1
    if ds["failures"] >= 3:
        ds["dead"] = True
    elif ds["successes"] >= 3:
        ds["stable"] = True
        ds["successes"] = 0
        ds["failures"] = 0
    return ch


# ------------------------------------------------------------------------------ rests

def _reset_counters(ch: dict, triggers: set[str]) -> None:
    for c in ch.get("counters", []):
        if c.get("reset") in triggers:
            c["value"] = int(c["max"])


def spend_hit_die(ch: dict, die: int, roll_value: int) -> dict:
    """Short-rest hit die: regain roll + CON mod (minimum 0)."""
    ch = deepcopy(ch)
    pools = [p for p in ch.get("hitDice", []) if int(p["die"]) == die]
    if not pools or pools[0]["used"] >= pools[0]["total"]:
        raise ValueError(f"no d{die} hit dice left")
    pools[0]["used"] += 1
    con = ability_mod(int(ch["abilities"]["con"]))
    heal = max(0, roll_value + con)
    ch["hp"]["damage"] = max(0, int(ch["hp"].get("damage", 0)) - heal)
    if int(ch["hp"]["max"]) - ch["hp"]["damage"] > 0:
        _reset_death_saves(ch)
    return ch


def short_rest(ch: dict) -> dict:
    """Reset short-rest counters and Pact Magic slots. (Hit dice are spent one at a time
    via spend_hit_die during the rest.)"""
    ch = deepcopy(ch)
    _reset_counters(ch, {"short"})
    sc = ch.get("spellcasting")
    if sc and sc.get("pactUsed") is not None:
        sc["pactUsed"] = 0
    return ch


def long_rest(ch: dict) -> dict:
    """2014 long rest: full HP; regain hit dice up to half the total (min 1), largest dice
    first; all spell slots; short+long counters; temp HP gone; exhaustion −1; death saves
    reset. Conditions are NOT cleared automatically (the DM decides)."""
    ch = deepcopy(ch)
    hp = ch["hp"]
    if ch.get("deathSaves", {}).get("dead"):
        return ch
    hp["damage"] = 0
    hp["temp"] = 0
    _reset_death_saves(ch)
    pools = sorted(ch.get("hitDice", []), key=lambda p: -int(p["die"]))
    total = sum(int(p["total"]) for p in pools)
    regain = max(1, total // 2) if total else 0
    for p in pools:
        give = min(regain, int(p["used"]))
        p["used"] = int(p["used"]) - give
        regain -= give
        if regain == 0:
            break
    _reset_counters(ch, {"short", "long"})
    sc = ch.get("spellcasting")
    if sc:
        if sc.get("slotsUsed") is not None:
            sc["slotsUsed"] = [0] * 9
        if sc.get("pactUsed") is not None:
            sc["pactUsed"] = 0
    ch["exhaustion"] = max(0, int(ch.get("exhaustion", 0)) - 1)
    return ch


def dawn(ch: dict) -> dict:
    ch = deepcopy(ch)
    _reset_counters(ch, {"dawn"})
    return ch


# ------------------------------------------------------------------------------ slots

def spend_slot(ch: dict, level: int) -> dict:
    """Spend one spell slot of `level` (1..9); prefers Pact slot if it matches exactly and
    regular slots are exhausted — the tool asks the player which to use, so this helper
    only handles regular slots and raises when none remain."""
    ch = deepcopy(ch)
    sc = ch.setdefault("spellcasting", {})
    used = sc.setdefault("slotsUsed", [0] * 9)
    maxes = spell_slots(ch["classes"])["slots"]
    if used[level - 1] >= maxes[level - 1]:
        raise ValueError(f"no level-{level} slots left")
    used[level - 1] += 1
    return ch


def spend_pact_slot(ch: dict) -> dict:
    ch = deepcopy(ch)
    sc = ch.setdefault("spellcasting", {})
    pact = spell_slots(ch["classes"])["pact"]
    if not pact:
        raise ValueError("no pact magic")
    used = int(sc.get("pactUsed", 0))
    if used >= pact["count"]:
        raise ValueError("no pact slots left")
    sc["pactUsed"] = used + 1
    return ch


# --------------------------------------------------------------------- hit point max

AVERAGE_HIT_DIE = {6: 4, 8: 5, 10: 6, 12: 7}


def hp_max_average(classes: list[dict], con_score: int, custom: dict | None = None) -> int:
    """Level-1 max die + CON, then the fixed average (die/2 + 1) + CON per later level,
    across classes in the order listed (first class gets the level-1 maximum)."""
    con = ability_mod(con_score)
    total = 0
    first = True
    for c in classes:
        die = hit_die_for(c["classKey"], c.get("custom"))
        for _ in range(int(c["level"])):
            gain = (die if first else AVERAGE_HIT_DIE[die]) + con
            first = False
            total += max(1, gain)  # SRD: you gain at least 1 hit point per level
    return total


# ----------------------------------------------------------------------- event runner

def apply_event(ch: dict, ev: dict) -> dict:
    kind = ev["type"]
    if kind == "damage":
        return apply_damage(ch, ev["amount"], ev.get("critical", False))
    if kind == "heal":
        return apply_healing(ch, ev["amount"])
    if kind == "temp":
        return apply_temp_hp(ch, ev["amount"])
    if kind == "deathSave":
        return death_save(ch, ev["d20"])
    if kind == "spendHitDie":
        return spend_hit_die(ch, ev["die"], ev["roll"])
    if kind == "shortRest":
        return short_rest(ch)
    if kind == "longRest":
        return long_rest(ch)
    if kind == "dawn":
        return dawn(ch)
    if kind == "spendSlot":
        return spend_slot(ch, ev["level"])
    if kind == "spendPactSlot":
        return spend_pact_slot(ch)
    if kind == "counter":
        ch = deepcopy(ch)
        for c in ch.get("counters", []):
            if c["id"] == ev["id"]:
                c["value"] = max(0, min(int(c["max"]), int(c["value"]) + int(ev["delta"])))
                return ch
        raise ValueError(f"unknown counter {ev['id']}")
    raise ValueError(f"unknown event {kind}")


def run(ch: dict, events: list[dict]) -> dict:
    for ev in events:
        ch = apply_event(ch, ev)
    return ch
