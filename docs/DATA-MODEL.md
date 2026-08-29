# Grimoire — Data model

Three data sets, three storage strategies. Everything is rebuildable from durable storage
because the SDK's nav stack and view models are process-memory only.

| Data | Source of truth | Storage | Size |
|---|---|---|---|
| **Compendium** (SRD 5.1) | committed JSON under `tool/src/main/assets/compendium/` | imported into Room on first launch (+ FTS4); read-only | 2.6 MB assets → ~3 MB DB with FTS4 (3.16 MB measured on a comparable set) |
| **Characters** | the player | Room table `characters` (one JSON document per row, `schemaVersion`) + hot columns for the list | KBs |
| **Journal** | the player | Room tables `sessions`, `entries`, `people`, `places`, `quests`, `loot` | KBs |
| Small prefs (last character, import hash) | — | `lightContext.dataStore` | bytes |

Schemas (JSON Schema 2020-12, validated in CI): `pipeline/schema/compendium.schema.json`,
`character.schema.json`, `journal.schema.json`.

## 1. Character

Stored fields (see the schema for types and limits) and their **invariants**:

- `hp = {max, damage, temp}` — current HP is **derived** (`max − damage`), never stored, so
  max can never be lost by a mis-tap (D&D Beyond `removedHitPoints` / Fantasy Grounds
  "wounds" pattern).
- `spellcasting.slotsUsed[9]` and `pactUsed` are stored; **maxima are derived** from
  `classes` via the class tables (single-class) or the Multiclass Spellcaster rule.
- `counters[]` is the one primitive for every limited-use thing: class features, hit dice
  pools are *not* counters (they have their own die-typed structure), item charges, custom
  homebrew uses. `reset ∈ short | long | dawn | none`.
- `abilities` are **final** scores (racial bonuses already applied) — that is what a paper
  sheet shows and what a player will transcribe.
- `ac` is either `manual` (transcribed) or `computed` (unarmored / armor key / monk /
  barbarian / mage armor + shield + bonus). Paper players default to manual.
- Non-SRD content is representable without text: `classes[].custom{hitDie, casterType}`,
  `customSubclassName`, `spells[].custom` (name + level), `attacks[]` with explicit ability
  and dice, `items[].custom`, `counters[]` with no `featureKey`.
- Death saves, conditions, exhaustion, concentration and inspiration are plain state.

Derived (engine output, never stored): ability mods, proficiency bonus, saves, skills,
passive perception, initiative, AC (computed mode), current HP / bloodied / down, spell DC
and attack bonus, max slots, attack to-hit and damage formulas. Python reference:
`pipeline/reference/rules.py::derive`; Kotlin: `rules/Derive.kt` (must replay
`fixtures/derived.json`).

Events (the only way state changes in play): `damage`, `heal`, `temp`, `deathSave`,
`spendHitDie`, `shortRest`, `longRest`, `dawn`, `spendSlot`, `spendPactSlot`, `counter`,
plus edits from the wizard. Reference: `rules.py::apply_event`; fixtures: `events.json`.

### Room mapping

```
characters(id TEXT PK, name TEXT, summary TEXT, updatedAt INTEGER, json TEXT)
```
One row per character; `json` is the schema document (kotlinx-serialization). `summary`
("Cleric 5 · Hill Dwarf") is denormalised for the Home list. Writes are debounced 400 ms
and wrapped in `withContext(NonCancellable)` (the Sudoku lesson: popping a screen cancels
`viewModelScope`). Migrations: `schemaVersion` in the JSON + a Kotlin `migrate(json)` chain;
Room's own schema stays at v1 until a hot column changes.

## 2. Compendium

Kinds and record counts (SRD 5.1 via 5e-bits @ `ce47a18`): spells 319 · creatures 334 ·
classes 12 · subclasses 12 · features 407 · races 9 · subraces 4 · traits 38 · backgrounds 1
· feats 1 · conditions 15 · equipment 237 · magic items 362 (239 base + variants) · weapon
properties 11 · skills 18 · languages 16 · damage types 13 · schools 8 · alignments 9 ·
proficiencies 117 · rules 9 · rule sections 40 (33 from 5e-bits + 7 SRD 5.1 sections merged
from Open5e v1: leveling up, multiclassing, inspiration, alignment, languages, reading a stat
block, nonplayer characters).

Envelope on every record: `key, name, edition("2014"), source("srd-5.1"), license("CC-BY-4.0"),
xref`. Prose is Markdown-lite: `\n\n` paragraphs, `- ` bullets, `**bold**`/`***bold italic***`,
`#`–`####` headings (rule sections) and `|` tables (rule sections, some spells and magic
items). The reader renders exactly those constructs and nothing else.

### On-device import

```
first launch (or index.bundleSha256 ≠ DataStore.importedHash):
  for each kind: readAsset("compendium/<kind>.json") → decode → insert in one transaction
  build FTS: search_index(kind, key, name, body)  -- @Fts4
  DataStore.importedHash = bundleSha256
```
Expect a few seconds on the LP3; show the Light spinner once. Room schema for the
compendium is generated from the Kotlin models, one table per kind with a `json` column and
the hot columns the lists need (`level`, `school`, `cr`, `category`, `rarity`). Never ship a
`.db` (builder rejects the extension); never mutate compendium tables.

## 3. Journal

Six entities, one link primitive (see `journal.schema.json`):

```
sessions(id, number, date, inWorldDate?, title?)
entries(id, sessionId, at, kind ∈ met|went|quest|got|learned|rumor|note,
        personId?, placeId?, questId?, lootId?, text ≤ 120)
people(id, name, party, metAtPlaceId?, detail ≤ 80, disposition, alive, firstSeenSession)
places(id, name, parentId?, detail, firstSeenSession)
quests(id, title, status ∈ open|done|failed|dropped, giverId?, placeId?, objectives[≤8])
loot(id, name, holderId?, sessionId?, valueGp?, status ∈ held|sold|used|given)
```
Backlinks ("every entry mentioning Merrick") are queries, never stored. Rosters sort by
recency of last mention. Export = the JSON document (schema) and a Markdown rendering
shaped for Obsidian (YAML `type/status/aliases` properties, `[[wikilinks]]`, one file per
entity and per session) — generated on the phone as text pages in v1, as QR pages later.

## 4. Dice

`Roll{expression, seed, rolls[][], kept[][], total, natural}`; RNG = mulberry32 seeded from
`System.nanoTime()` in play and from the fixture seed in tests; the grammar is the
Roll20 subset in `pipeline/reference/dice.py`. The last ten rolls live in the Dice view
model only (intentionally not persisted).

## 5. Identifiers

Compendium keys are the upstream `index` slugs (`fireball`, `chain-mail`). Character and
journal ids are `UUID.randomUUID().toString()`. Counter ids seeded from the class tables use
the feature key (`channel-divinity`), custom ones a UUID.
