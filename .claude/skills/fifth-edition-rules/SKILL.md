---
name: fifth-edition-rules
description: 2014-rules (SRD 5.1) math and procedures the Grimoire engine implements — ability modifiers, proficiency, saves/skills, AC formulas, hit points and the HP ledger, death saves, temp HP, short/long rests, spell slots (single-class tables and the multiclass rule), pact magic, conditions and exhaustion — each mapped to its reference function and fixture. Load whenever touching rules/, Derive/Ledger, the HP/Rest/Spells screens, or any fixture.
user-invocable: false
---

# 2014 rules the engine implements (SRD 5.1)

Authority order: SRD text (compendium `rule_sections/*`) → `pipeline/reference/rules.py`
(the oracle) → `fixtures/*.json` → Kotlin `rules/`. Change in that order. Ask the
`rules-lawyer` agent for citations; use the `compendium` MCP tools for text.

## Core numbers

| Rule | Formula | Reference fn | Fixture |
|---|---|---|---|
| Ability modifier | `floor((score − 10) / 2)` — floor, not truncate (8 → −1, 9 → −1) | `ability_mod` | `math.json` |
| Proficiency bonus | `2 + floor((level − 1) / 4)` (total character level) | `proficiency_bonus` | `math.json` |
| Saving throw | mod (+ prof if proficient) | `derive` | `derived.json` |
| Skill | mod + prof × {0, ½ (floor), 1, 2} | `derive` | `derived.json` |
| Passive Perception | 10 + Perception bonus | `derive` | `derived.json` |
| Initiative | DEX mod (+ `initiativeBonus`) | `derive` | `derived.json` |
| Spell save DC / attack | 8 + prof + casting mod / prof + casting mod | `derive` | `derived.json` |
| Weapon attack | ability mod + prof (if proficient) + `bonus`; damage = dice + ability mod (unless `damageBonusMode = none`) | `derive` | `derived.json` |
| HP max (average method) | L1: max die + CON; each later level: `die/2 + 1` + CON; **min 1 per level** | `hp_max_average` | `math.json` |

Armor class (`ac.formula`): unarmored 10 + DEX; light armor base + DEX; medium base +
min(DEX, 2); heavy base (STR minimum is advisory, not enforced); shield +2; monk
10 + DEX + WIS (no armor/shield); barbarian 10 + DEX + CON (shield allowed); mage armor
13 + DEX. Armor base values come from `equipment.json` (`armor.base/dexBonus/maxBonus`).
Paper players default to `mode: manual`.

## Hit points and the ledger (`Ledger`)

- Store `max`, `damage`, `temp`; current = `max − damage`. Never store current.
- **Damage**: temp HP absorb first (they are lost, never carried) — **also while at 0 HP**
  ("they can still absorb damage directed at you while you're in that state"); remaining
  damage reduces HP; at 0 HP any damage that gets through = one death-save failure (two on
  a critical hit); if damage drops you to 0 **and** the overflow ≥ max HP → instant death;
  if damage taken at 0 HP ≥ max HP → instant death.
- **Healing** cannot exceed max; regaining ≥ 1 HP clears death saves; does nothing if dead.
- **Temp HP** do not stack: keep the higher value. Cleared by a long rest. That is the *grant*
  (`temp`); a *correction* to the number on the sheet (`tempDelta`) is signed, clamped at 0,
  does stack, and is the only way temp HP comes down. Neither touches HP or the death saves, so
  `tempDelta` alone among the HP-adjacent functions keeps working while dead — on purpose.
- **A dead character benefits from nothing**: `heal`, `spendHitDie`, `longRest` and `deathSave`
  all return the character unchanged once `deathSaves.dead`. `spendHitDie` checks `dead` *before*
  the pool, so a dead character spending a die they lack is a no-op, not the "no dice left" error.
- **Death saves**: d20 ≥ 10 success, < 10 failure, natural 1 = two failures, natural 20 =
  regain 1 HP; three successes → stable (counters reset, still at 0 HP); three failures →
  dead. Damage while stable resumes saves and counts a failure.
- Hit dice pools are per die size: `{die, total, used}`.

## Rests

- **Short rest** (≥ 1 hour): spend hit dice one at a time — regain `roll + CON mod`
  (min 0); reset counters with `reset = short`; Pact Magic slots reset. Nothing else.
- **Long rest** (≥ 8 hours): full HP; temp HP gone; regain hit dice up to `floor(total/2)`
  (min 1), largest dice first; all spell slots and pact slots; counters `short` + `long`;
  exhaustion −1; death saves cleared. Requires ≥ 1 HP to benefit (warn, don't block).
  Conditions are never auto-cleared.
- **Dawn** counters reset on an explicit `dawn` event (the tool offers it alongside long rest).

## Spell slots

- Single class: the class table. Full casters (bard, cleric, druid, sorcerer, wizard)
  = `FULL_CASTER_SLOTS[level]`; half casters (paladin, ranger) = full table at
  `ceil(level/2)` from level 2; third casters (not in the SRD; `custom.casterType = third`)
  = full table at `ceil(level/3)` from level 3. Verified against `classes.json` in tests.
- Two or more classes **with the Spellcasting feature**: caster level = full +
  `floor(half/2)` + `floor(third/3)`, then the full table (SRD: "If you multiclass but have
  the Spellcasting feature from only one class, you follow the rules as described in that
  class"). So Paladin 3 / Fighter 1 keeps the paladin table (three 1st-level slots) while
  Paladin 3 / Wizard 1 is caster level 2 (three 1st-level slots, one class table lost).
- Warlock Pact Magic is separate: `PACT_SLOTS[level] = (count, slotLevel)`; never merged.
- Prepared casters: cleric, druid and wizard prepare `casting mod + class level` (min 1);
  a paladin prepares `CHA mod + floor(paladin level / 2)` (min 1). Known casters (bard,
  ranger, sorcerer, warlock) use the table's `spellsKnown`. The tool
  shows the allowance and never blocks over-preparing (paper tables differ).

## Conditions and exhaustion (2014)

Fifteen conditions with SRD text in `conditions.json`; exhaustion is a 0–6 level with
cumulative effects: 1 disadvantage on ability checks; 2 speed halved; 3 disadvantage on
attacks and saves; 4 HP max halved; 5 speed 0; 6 death. Long rest reduces it by 1.
Concentration: one spell at a time; casting another concentration spell ends the first;
damage prompts a CON save DC `max(10, floor(damage/2))` — the tool shows the DC, the player rolls.

## Vocabulary (2014 vs 2024)

race (not species) · Hit Dice · inspiration (not heroic) · the SRD 5.1 text itself says
"GM"/"game master" throughout (never "DM") — use "GM" in UI copy too.

Details and edge cases with citations: `references/edge-cases.md`. The multiclassing and
leveling-up text is bundled (`rule_sections/multiclassing`, `rule_sections/leveling-up`,
merged from Open5e's SRD 5.1 sections).
