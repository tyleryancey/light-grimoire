---
name: character-model
description: The Grimoire character and journal data model — stored vs derived fields, invariants (HP = max − damage; slot maxima derived; counters as the single limited-use primitive), the in-play event vocabulary, Room mapping, migration rules, and how non-SRD content is represented without text. Load when editing rules/Model.kt, repositories, Room entities, the wizard, import/export, or any screen that mutates character state.
user-invocable: false
---

# Character & journal model

Schemas are law: `pipeline/schema/character.schema.json`, `journal.schema.json`,
`compendium.schema.json` (validated in CI; the fixtures conform). Prose: `docs/DATA-MODEL.md`.

## Stored vs derived (character)

Stored: identity, `classes[]` (key/subclass/level/custom), `race`, `background`,
**final** `abilities`, `saveProficiencies`, `skills{key: none|half|proficient|expertise}`,
`ac{mode…}`, `speed`, `initiativeBonus`, `hp{max,damage,temp}`, `hitDice[{die,total,used}]`,
`deathSaves`, `conditions[]`, `exhaustion`, `concentration`, `inspiration`,
`counters[]`, `spellcasting{ability,mode,slotsUsed[9],pactUsed,spells[]}`, `attacks[]`,
`items[]`, `currency`, `notes[]`, `meta`.

Derived (never persisted; recompute on every read): mods, prof, saves, skills, passive
perception, initiative, AC (computed mode), current HP / bloodied / down, DC, spell attack,
slot maxima, attack to-hit and damage formulas. Kotlin: `Derive.derive(character, armorTable)`.

## Invariants the code must keep

1. `0 ≤ damage ≤ max`; `temp ≥ 0`; current HP = `max − damage`.
2. `slotsUsed[i] ≤ slotsMax[i]` after any level/class edit — clamp on edit, never throw.
3. `counters[].value ∈ [0, max]`; ids unique; seeded counters carry `featureKey`.
4. `attuned` items ≤ 3.
5. A character with `custom` classes must declare `hitDie` (and `casterType` if it casts).
6. `schemaVersion` bumps only with a `migrate()` step; old JSON always loads.

## Events (the only mutation vocabulary in play)

`damage{amount,critical}`, `heal{amount}`, `temp{amount}`, `deathSave{d20}`,
`spendHitDie{die,roll}`, `shortRest`, `longRest`, `dawn`, `spendSlot{level}`,
`spendPactSlot`, `counter{id,delta}` — plus `restoreSlot{level}`, `toggleCondition{key}`,
`setExhaustion{level}`, `setConcentration{spellKey|null}`, `toggleInspiration`,
`setCurrency{…}`, `toggleEquipped{id}`, `toggleAttuned{id}` which are trivial setters (no
fixture needed) and are applied by the view model directly. Everything with rules
semantics goes through `Ledger` and has a fixture in `events.json`.

## Representing non-SRD content (paper tables)

| On the paper sheet | In the model |
|---|---|
| Battle Master subclass | `classes[0].subclassKey = null`, `customSubclassName = "Battle Master"`, counters `Superiority Dice (4, short)` |
| A PHB spell not in the SRD | `spells[] += {key: null, name, level, custom: true}` — no text on device |
| Homebrew magic sword +1 | `items[] += {key: null, name, custom: true}` + `attacks[] += {name, ability: str, bonus: 1, damage: "1d8", damageBonus: 1}` |
| Feat with uses | a counter with `reset` |
| Non-SRD race | `race{key: null, name}`; abilities are final anyway |

## Persistence

Room `characters(id, name, summary, updatedAt, json)`; write via the repository with a
400 ms debounce and `withContext(NonCancellable)`; the Home list reads only the hot
columns. Journal tables per `journal.schema.json`; backlinks are queries. Compendium tables
are immutable after import (bundle hash in DataStore).

## Ids and names

Compendium keys are upstream slugs. New ids: `UUID.randomUUID()`. Names ≤ 40 chars,
entered with `LightTextInputEditor(singleLine = true, initialCaps = true)`.
