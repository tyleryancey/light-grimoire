"""Adapter: Open5e v1 `data/v1/wotc-srd/Section.json` → supplemental rule sections.

5e-bits' 33 rule sections omit several SRD 5.1 chapters a player needs on the phone
(multiclassing, leveling up, inspiration, alignment, languages, reading a stat block, NPC
customisation). Open5e's v1 fixtures carry them as CC-BY SRD text. We merge exactly the
sections listed in SUPPLEMENT, grouped under three new `rules` entries, and leave every
5e-bits section untouched so keys stay stable.
"""
from __future__ import annotations

import json
from pathlib import Path

from .common import sorted_records, stamp

# open5e pk → (our key, display name, rules group)
SUPPLEMENT: dict[str, tuple[str, str, str]] = {
    "leveling-up": ("leveling-up", "Leveling Up", "beyond-1st-level"),
    "multiclassing": ("multiclassing", "Multiclassing", "beyond-1st-level"),
    "inspiration": ("inspiration", "Inspiration", "characters"),
    "alignment": ("alignment", "Alignment", "characters"),
    "languages": ("languages", "Languages", "characters"),
    "monsters": ("reading-a-stat-block", "Monsters: Reading a Stat Block", "monsters-and-npcs"),
    "nonplayer-characters": ("nonplayer-characters", "Nonplayer Characters", "monsters-and-npcs"),
}

GROUPS: dict[str, str] = {
    "beyond-1st-level": "Beyond 1st Level",
    "characters": "Characters",
    "monsters-and-npcs": "Monsters and NPCs",
}


def supplement(cache: Path) -> tuple[list[dict], list[dict]]:
    """Returns (extra_rules, extra_rule_sections)."""
    raw = json.loads((cache / "Section.json").read_text(encoding="utf-8"))
    by_pk = {r["pk"]: r["fields"] for r in raw}
    sections = []
    grouped: dict[str, list[str]] = {g: [] for g in GROUPS}
    for pk, (key, name, group) in SUPPLEMENT.items():
        if pk not in by_pk:
            raise RuntimeError(f"open5e v1 Section.json lacks {pk!r}")
        rec = stamp(key, name, f"open5e:v1:section:{pk}")
        rec["text"] = (by_pk[pk].get("desc") or "").strip()
        sections.append(rec)
        grouped[group].append(key)
    rules = []
    for group, title in GROUPS.items():
        rec = stamp(group, title, "open5e:v1:section-group")
        rec.update({"text": f"# {title}\n", "sections": grouped[group]})
        rules.append(rec)
    return sorted_records(rules), sorted_records(sections)
