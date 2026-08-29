"""Adapter: 5e-bits/5e-database `src/2014/en` → unified compendium (SRD 5.1, 2014 rules).

Input: the 25 `5e-SRD-*.json` files at the pinned commit (see sources.lock.json).
Output: a dict `{kind: [records…]}` matching pipeline/schema/compendium.schema.json.

Design rules:
- Keep the upstream `index` as our `key` (stable, lowercase, hyphenated).
- Cross-references become bare keys (never API URLs).
- Prose is joined into Markdown-lite `text`; we never rewrite rules wording.
- Everything typed that the phone UI needs is lifted into fields (level, school,
  slots, damage dice…); everything else stays in `text`.
"""
from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from .common import (
    option_desc,
    paragraphs,
    parse_attunement,
    ref_key,
    ref_keys,
    sorted_records,
    stamp,
)

ABILITIES = ["str", "dex", "con", "int", "wis", "cha"]


def _load(cache: Path, name: str) -> list[dict]:
    return json.loads((cache / f"5e-SRD-{name}.json").read_text(encoding="utf-8"))


# --------------------------------------------------------------------------- spells

def spells(cache: Path) -> list[dict]:
    out = []
    for s in _load(cache, "Spells"):
        rec = stamp(s["index"], s["name"], s.get("url"))
        dmg = s.get("damage") or {}
        rec.update(
            {
                "level": s["level"],
                "school": ref_key(s.get("school")),
                "castingTime": s.get("casting_time", ""),
                "range": s.get("range", ""),
                "components": s.get("components", []),
                "material": s.get("material"),
                "ritual": bool(s.get("ritual")),
                "concentration": bool(s.get("concentration")),
                "duration": s.get("duration", ""),
                "classes": ref_keys(s.get("classes")),
                "subclasses": ref_keys(s.get("subclasses")),
                "attackType": s.get("attack_type"),
                "saveAbility": ref_key((s.get("dc") or {}).get("dc_type")),
                "saveSuccess": (s.get("dc") or {}).get("dc_success"),
                "damageType": ref_key(dmg.get("damage_type")),
                "damageAtSlotLevel": dmg.get("damage_at_slot_level"),
                "damageAtCharacterLevel": dmg.get("damage_at_character_level"),
                "healAtSlotLevel": s.get("heal_at_slot_level"),
                "areaOfEffect": s.get("area_of_effect"),
                "text": paragraphs(s.get("desc")),
                "higherLevel": paragraphs(s.get("higher_level")),
            }
        )
        out.append(rec)
    return sorted_records(out)


# ------------------------------------------------------------------------ creatures

def _creature_action(a: dict) -> dict:
    rec: dict[str, Any] = {"name": a.get("name", ""), "text": paragraphs(a.get("desc"))}
    if a.get("attack_bonus") is not None:
        rec["attackBonus"] = a["attack_bonus"]
    if a.get("damage"):
        rec["damage"] = [
            {"dice": d.get("damage_dice"), "type": ref_key(d.get("damage_type"))}
            for d in a["damage"]
            if isinstance(d, dict) and d.get("damage_dice")
        ]
    dc = a.get("dc")
    if dc:
        rec["save"] = {"ability": ref_key(dc.get("dc_type")), "dc": dc.get("dc_value"), "success": dc.get("success_type")}
    if a.get("usage"):
        u = a["usage"]
        rec["usage"] = {k: u[k] for k in ("type", "times", "dice", "min_value", "rest_types") if k in u}
    return rec


def creatures(cache: Path) -> list[dict]:
    out = []
    for m in _load(cache, "Monsters"):
        saves: dict[str, int] = {}
        skills: dict[str, int] = {}
        for p in m.get("proficiencies", []):
            idx = p["proficiency"]["index"]
            if idx.startswith("saving-throw-"):
                saves[idx.removeprefix("saving-throw-")] = p["value"]
            elif idx.startswith("skill-"):
                skills[idx.removeprefix("skill-")] = p["value"]
        rec = stamp(m["index"], m["name"], m.get("url"))
        rec.update(
            {
                "size": m.get("size"),
                "type": m.get("type"),
                "subtype": m.get("subtype"),
                "alignment": m.get("alignment"),
                "ac": [{"value": a.get("value"), "type": a.get("type")} for a in m.get("armor_class", [])],
                "hp": m.get("hit_points"),
                "hitDice": m.get("hit_dice"),
                "hpRoll": m.get("hit_points_roll"),
                "speed": m.get("speed", {}),
                "abilities": {
                    "str": m.get("strength"),
                    "dex": m.get("dexterity"),
                    "con": m.get("constitution"),
                    "int": m.get("intelligence"),
                    "wis": m.get("wisdom"),
                    "cha": m.get("charisma"),
                },
                "saves": saves,
                "skills": skills,
                "vulnerabilities": m.get("damage_vulnerabilities", []),
                "resistances": m.get("damage_resistances", []),
                "immunities": m.get("damage_immunities", []),
                "conditionImmunities": ref_keys(m.get("condition_immunities")),
                "senses": m.get("senses", {}),
                "languages": m.get("languages", ""),
                "cr": m.get("challenge_rating"),
                "profBonus": m.get("proficiency_bonus"),
                "xp": m.get("xp"),
                "traits": [_creature_action(a) for a in m.get("special_abilities", [])],
                "actions": [_creature_action(a) for a in m.get("actions", [])],
                "reactions": [_creature_action(a) for a in m.get("reactions", [])],
                "legendaryActions": [_creature_action(a) for a in m.get("legendary_actions", [])],
                "text": paragraphs(m.get("desc")),
            }
        )
        out.append(rec)
    return sorted_records(out)


# -------------------------------------------------------------- classes & progression

def _slots_from(sc: dict | None) -> list[int]:
    if not sc:
        return [0] * 9
    return [int(sc.get(f"spell_slots_level_{i}", 0) or 0) for i in range(1, 10)]


def classes(cache: Path) -> tuple[list[dict], list[dict], list[dict]]:
    """Returns (classes, subclasses, features)."""
    levels = _load(cache, "Levels")
    by_class: dict[str, list[dict]] = {}
    by_subclass: dict[str, list[dict]] = {}
    for lv in levels:
        if "subclass" in lv:
            by_subclass.setdefault(lv["subclass"]["index"], []).append(lv)
        else:
            by_class.setdefault(lv["class"]["index"], []).append(lv)

    cls_out = []
    for c in _load(cache, "Classes"):
        rows = sorted(by_class.get(c["index"], []), key=lambda r: r["level"])
        spell = c.get("spellcasting") or {}
        rec = stamp(c["index"], c["name"], c.get("url"))
        rec.update(
            {
                "hitDie": c["hit_die"],
                "savingThrows": ref_keys(c.get("saving_throws")),
                "proficiencies": ref_keys(c.get("proficiencies")),
                "proficiencyChoices": [
                    {"choose": pc.get("choose"), "desc": option_desc(pc), "from": [
                        ref_key(o.get("item")) for o in (pc.get("from", {}).get("options") or []) if o.get("item")
                    ]}
                    for pc in c.get("proficiency_choices", [])
                ],
                "startingEquipment": [
                    {"key": ref_key(e.get("equipment")), "qty": e.get("quantity", 1)}
                    for e in c.get("starting_equipment", [])
                ],
                "startingEquipmentOptions": [option_desc(o) for o in c.get("starting_equipment_options", [])],
                "multiclassing": {
                    "prerequisites": [
                        {"ability": ref_key(p.get("ability_score")), "minimum": p.get("minimum_score")}
                        for p in (c.get("multi_classing") or {}).get("prerequisites", [])
                    ],
                    "prerequisiteOptions": option_desc((c.get("multi_classing") or {}).get("prerequisite_options") or {}),
                    "proficiencies": ref_keys((c.get("multi_classing") or {}).get("proficiencies")),
                },
                "spellcasting": (
                    {
                        "ability": ref_key(spell.get("spellcasting_ability")),
                        "startsAtLevel": spell.get("level"),
                        "info": [{"name": i.get("name"), "text": paragraphs(i.get("desc"))} for i in spell.get("info", [])],
                    }
                    if spell
                    else None
                ),
                "subclasses": ref_keys(c.get("subclasses")),
                "levels": [
                    {
                        "level": r["level"],
                        "profBonus": r.get("prof_bonus"),
                        "abilityScoreBonuses": r.get("ability_score_bonuses", 0),
                        "features": ref_keys(r.get("features")),
                        "slots": _slots_from(r.get("spellcasting")),
                        "cantripsKnown": (r.get("spellcasting") or {}).get("cantrips_known"),
                        "spellsKnown": (r.get("spellcasting") or {}).get("spells_known"),
                        "classSpecific": r.get("class_specific") or {},
                    }
                    for r in rows
                ],
            }
        )
        cls_out.append(rec)

    sub_out = []
    for s in _load(cache, "Subclasses"):
        rows = sorted(by_subclass.get(s["index"], []), key=lambda r: r["level"])
        rec = stamp(s["index"], s["name"], s.get("url"))
        rec.update(
            {
                "classKey": ref_key(s.get("class")),
                "flavor": s.get("subclass_flavor"),
                "text": paragraphs(s.get("desc")),
                "spells": [
                    {"key": ref_key(sp.get("spell")), "prerequisites": [
                        {"type": p.get("type"), "level": p.get("level"), "feature": p.get("index")}
                        for p in sp.get("prerequisites", [])
                    ]}
                    for sp in s.get("spells", [])
                ],
                "levels": [
                    {"level": r["level"], "features": ref_keys(r.get("features")), "classSpecific": r.get("subclass_specific") or {}}
                    for r in rows
                ],
            }
        )
        sub_out.append(rec)

    feat_out = []
    for f in _load(cache, "Features"):
        rec = stamp(f["index"], f["name"], f.get("url"))
        rec.update(
            {
                "classKey": ref_key(f.get("class")),
                "subclassKey": ref_key(f.get("subclass")),
                "level": f.get("level"),
                "parentKey": ref_key(f.get("parent")),
                "prerequisites": f.get("prerequisites", []),
                "featureSpecific": {k: v for k, v in (f.get("feature_specific") or {}).items() if k != "subfeature_options"},
                "text": paragraphs(f.get("desc")),
            }
        )
        feat_out.append(rec)
    return sorted_records(cls_out), sorted_records(sub_out), sorted_records(feat_out)


# ------------------------------------------------------------------ races & traits

def races(cache: Path) -> tuple[list[dict], list[dict], list[dict]]:
    race_out = []
    for r in _load(cache, "Races"):
        rec = stamp(r["index"], r["name"], r.get("url"))
        rec.update(
            {
                "speed": r.get("speed"),
                "size": r.get("size"),
                "abilityBonuses": [{"ability": ref_key(b.get("ability_score")), "bonus": b.get("bonus")} for b in r.get("ability_bonuses", [])],
                "abilityBonusOptions": option_desc(r.get("ability_bonus_options") or {}),
                "languages": ref_keys(r.get("languages")),
                "languageOptions": option_desc(r.get("language_options") or {}),
                "startingProficiencies": ref_keys(r.get("starting_proficiencies")),
                "startingProficiencyOptions": option_desc(r.get("starting_proficiency_options") or {}),
                "traits": ref_keys(r.get("traits")),
                "subraces": ref_keys(r.get("subraces")),
                "text": paragraphs([r.get("alignment"), r.get("age"), r.get("size_description"), r.get("language_desc")]),
            }
        )
        race_out.append(rec)
    sub_out = []
    for s in _load(cache, "Subraces"):
        rec = stamp(s["index"], s["name"], s.get("url"))
        rec.update(
            {
                "raceKey": ref_key(s.get("race")),
                "abilityBonuses": [{"ability": ref_key(b.get("ability_score")), "bonus": b.get("bonus")} for b in s.get("ability_bonuses", [])],
                "startingProficiencies": ref_keys(s.get("starting_proficiencies")),
                "languages": ref_keys(s.get("languages")),
                "languageOptions": option_desc(s.get("language_options") or {}),
                "traits": ref_keys(s.get("racial_traits")),
                "text": paragraphs(s.get("desc")),
            }
        )
        sub_out.append(rec)
    trait_out = []
    for t in _load(cache, "Traits"):
        rec = stamp(t["index"], t["name"], t.get("url"))
        rec.update(
            {
                "races": ref_keys(t.get("races")),
                "subraces": ref_keys(t.get("subraces")),
                "proficiencies": ref_keys(t.get("proficiencies")),
                "parentKey": ref_key(t.get("parent")),
                "text": paragraphs(t.get("desc")),
            }
        )
        trait_out.append(rec)
    return sorted_records(race_out), sorted_records(sub_out), sorted_records(trait_out)


# ------------------------------------------------------- backgrounds, feats, conditions

def backgrounds(cache: Path) -> list[dict]:
    out = []
    for b in _load(cache, "Backgrounds"):
        rec = stamp(b["index"], b["name"], b.get("url"))
        feature = b.get("feature") or {}
        rec.update(
            {
                "skillProficiencies": ref_keys(b.get("starting_proficiencies")),
                "languageOptions": option_desc(b.get("language_options") or {}),
                "startingEquipment": [{"key": ref_key(e.get("equipment")), "qty": e.get("quantity", 1)} for e in b.get("starting_equipment", [])],
                "startingEquipmentOptions": [option_desc(o) for o in b.get("starting_equipment_options", [])],
                "startingGold": (b.get("starting_gold") or {}).get("quantity"),
                "feature": {"name": feature.get("name"), "text": paragraphs(feature.get("desc"))},
                "personalityTraits": [o.get("string") for o in ((b.get("personality_traits") or {}).get("from", {}).get("options") or [])],
                "ideals": [o.get("desc") for o in ((b.get("ideals") or {}).get("from", {}).get("options") or [])],
                "bonds": [o.get("string") for o in ((b.get("bonds") or {}).get("from", {}).get("options") or [])],
                "flaws": [o.get("string") for o in ((b.get("flaws") or {}).get("from", {}).get("options") or [])],
                "text": "",
            }
        )
        out.append(rec)
    return sorted_records(out)


def feats(cache: Path) -> list[dict]:
    out = []
    for f in _load(cache, "Feats"):
        rec = stamp(f["index"], f["name"], f.get("url"))
        rec.update(
            {
                "prerequisites": [{"ability": ref_key(p.get("ability_score")), "minimum": p.get("minimum_score")} for p in f.get("prerequisites", [])],
                "text": paragraphs(f.get("desc")),
            }
        )
        out.append(rec)
    return sorted_records(out)


def conditions(cache: Path) -> list[dict]:
    out = []
    for c in _load(cache, "Conditions"):
        rec = stamp(c["index"], c["name"], c.get("url"))
        rec["text"] = paragraphs(c.get("desc"))
        out.append(rec)
    return sorted_records(out)


# ------------------------------------------------------------------ equipment & items

def equipment(cache: Path) -> list[dict]:
    out = []
    for e in _load(cache, "Equipment"):
        rec = stamp(e["index"], e["name"], e.get("url"))
        cost = e.get("cost") or {}
        rec.update(
            {
                "category": ref_key(e.get("equipment_category")),
                "cost": {"qty": cost.get("quantity"), "unit": cost.get("unit")} if cost else None,
                "weight": e.get("weight"),
                "text": paragraphs(e.get("desc")),
            }
        )
        if e.get("weapon_category"):
            dmg = e.get("damage") or {}
            two = e.get("two_handed_damage") or {}
            rec["weapon"] = {
                "category": e.get("weapon_category"),
                "rangeType": e.get("weapon_range"),
                "damage": {"dice": dmg.get("damage_dice"), "type": ref_key(dmg.get("damage_type"))} if dmg else None,
                "twoHandedDamage": {"dice": two.get("damage_dice"), "type": ref_key(two.get("damage_type"))} if two else None,
                "range": e.get("range"),
                "throwRange": e.get("throw_range"),
                "properties": ref_keys(e.get("properties")),
            }
        if e.get("armor_category"):
            ac = e.get("armor_class") or {}
            rec["armor"] = {
                "category": e.get("armor_category"),
                "base": ac.get("base"),
                "dexBonus": bool(ac.get("dex_bonus")),
                "maxBonus": ac.get("max_bonus"),
                "strMinimum": e.get("str_minimum"),
                "stealthDisadvantage": bool(e.get("stealth_disadvantage")),
            }
        if e.get("gear_category"):
            rec["gearCategory"] = ref_key(e.get("gear_category"))
        if e.get("tool_category"):
            rec["toolCategory"] = e.get("tool_category")
        if e.get("vehicle_category"):
            rec["vehicleCategory"] = e.get("vehicle_category")
        if e.get("contents"):
            rec["contents"] = [{"key": ref_key(c.get("item")), "qty": c.get("quantity", 1)} for c in e["contents"]]
        out.append(rec)
    return sorted_records(out)


def magic_items(cache: Path) -> list[dict]:
    out = []
    for i in _load(cache, "Magic-Items"):
        desc = i.get("desc") or []
        first = desc[0] if desc else ""
        attune, attune_by = parse_attunement(first)
        rec = stamp(i["index"], i["name"], i.get("url"))
        rec.update(
            {
                "category": ref_key(i.get("equipment_category")),
                "rarity": (i.get("rarity") or {}).get("name"),
                "attunement": attune,
                "attunementBy": attune_by,
                "isVariant": bool(i.get("variant")),
                "variants": ref_keys(i.get("variants")),
                "headline": first,
                "text": paragraphs(desc[1:]),
            }
        )
        out.append(rec)
    return sorted_records(out)


# ---------------------------------------------------------------------- small tables

def simple_table(cache: Path, name: str, extra: dict[str, str] | None = None) -> list[dict]:
    """Conditions-like tables: key/name/text plus optional lifted scalar fields."""
    out = []
    for r in _load(cache, name):
        rec = stamp(r["index"], r["name"], r.get("url"))
        rec["text"] = paragraphs(r.get("desc"))
        for src, dst in (extra or {}).items():
            val = r.get(src)
            if isinstance(val, dict) and "index" in val:
                val = val["index"]
            rec[dst] = val
        out.append(rec)
    return sorted_records(out)


def skills(cache: Path) -> list[dict]:
    return simple_table(cache, "Skills", {"ability_score": "ability"})


def languages(cache: Path) -> list[dict]:
    out = []
    for r in _load(cache, "Languages"):
        rec = stamp(r["index"], r["name"], r.get("url"))
        rec.update({"type": r.get("type"), "script": r.get("script"), "typicalSpeakers": r.get("typical_speakers", []), "text": paragraphs(r.get("desc"))})
        out.append(rec)
    return sorted_records(out)


def proficiencies(cache: Path) -> list[dict]:
    out = []
    for r in _load(cache, "Proficiencies"):
        rec = stamp(r["index"], r["name"], r.get("url"))
        ref = r.get("reference") or {}
        rec.update({"type": r.get("type"), "classes": ref_keys(r.get("classes")), "races": ref_keys(r.get("races")), "referenceKey": ref.get("index")})
        out.append(rec)
    return sorted_records(out)


def rules(cache: Path) -> tuple[list[dict], list[dict]]:
    rules_out = []
    for r in _load(cache, "Rules"):
        rec = stamp(r["index"], r["name"], r.get("url"))
        rec.update({"text": paragraphs(r.get("desc")), "sections": ref_keys(r.get("subsections"))})
        rules_out.append(rec)
    sec_out = []
    for r in _load(cache, "Rule-Sections"):
        rec = stamp(r["index"], r["name"], r.get("url"))
        rec["text"] = paragraphs(r.get("desc"))
        sec_out.append(rec)
    return sorted_records(rules_out), sorted_records(sec_out)


# ------------------------------------------------------------------------ entrypoint

def normalize(cache: Path) -> dict[str, list[dict]]:
    cls, sub, feats_ = classes(cache)
    race, subrace, traits_ = races(cache)
    rules_, sections = rules(cache)
    return {
        "spells": spells(cache),
        "creatures": creatures(cache),
        "classes": cls,
        "subclasses": sub,
        "features": feats_,
        "races": race,
        "subraces": subrace,
        "traits": traits_,
        "backgrounds": backgrounds(cache),
        "feats": feats(cache),
        "conditions": conditions(cache),
        "equipment": equipment(cache),
        "magic_items": magic_items(cache),
        "weapon_properties": simple_table(cache, "Weapon-Properties"),
        "skills": skills(cache),
        "languages": languages(cache),
        "damage_types": simple_table(cache, "Damage-Types"),
        "magic_schools": simple_table(cache, "Magic-Schools"),
        "alignments": simple_table(cache, "Alignments", {"abbreviation": "abbreviation"}),
        "proficiencies": proficiencies(cache),
        "rules": rules_,
        "rule_sections": sections,
    }
