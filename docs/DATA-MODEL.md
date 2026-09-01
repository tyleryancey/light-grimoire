# Grimoire — Data model

Three data sets, three storage strategies. Everything is rebuildable from durable storage
because the SDK's nav stack and view models are process-memory only.

| Data | Source of truth | Storage | Size |
|---|---|---|---|
| **Compendium** (SRD 5.1) | committed JSON under `tool/src/main/assets/compendium/` | imported into Room on first launch — one `records` table + `search_index` FTS4 in `compendium-v1.db`; read-only (ADR-0009) | 2.6 MB assets → 6.3 MB `compendium-v1.db` on the LP3 (+ a WAL of the same size until checkpoint) |
| **Characters** | the player | Room table `characters` (one JSON document per row, `schemaVersion`) + hot columns for the list — in `grimoire.db`, a separate file from the compendium | KBs |
| **Journal** | the player | Room tables `sessions`, `entries`, `people`, `places`, `quests`, `loot` — its own file, `journal.db` (M5) | KBs |
| Small prefs (last character, `compendium.stamp`) | — | `lightContext.dataStore` | bytes |

**Decision (Tyler, M3 repair pass): `grimoire.db` holds only `characters`.** The journal gets
its own `journal.db`, built in M5 (ADR-0010, forthcoming). `buildDatabase` exposes no migration
API (the same shape ADR-0009 already lives with for the compendium), so entities can never be
added to a Room database after it first ships on a phone — the alternative was freezing six
journal entities, none of them exercised before M5, onto the one file that holds a player's
characters and can never be rebuilt from source.

**This supersedes ADR-0009 §7**, which reads "Characters and the journal (M3+) go in a separate
`grimoire.db`". Only the characters half of that sentence still stands. ADR-0010 owes §7 a
superseded-by note, and until it carries one a reader following the ADR-0009 citation above
lands on the contradicted text — `docs/adr/` was outside the editable set of this repair pass.

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
- **Open gap:** `pipeline/schema/character.schema.json` bounds `classes` (≤ 3), `attacks`
  (≤ 12), `items` (≤ 60) and `notes` (≤ 20), but `counters[]`, `spellcasting.spells[]`,
  `conditions[]` and `hitDice[]` carry no `maxItems` at all — a gap against the global "every
  list bounded" rule and M6's finite-by-rule audit. Two of the four are derivable, not a
  judgement call: `conditions[]` ≤ 14 (the bundle's 15 conditions minus Exhaustion, which this
  same schema's description already calls out as tracked separately) and `hitDice[]` ≤ 4 (one
  pool per value in the schema's own `die` enum, `{6, 8, 10, 12}` — a character cannot have two
  pools of the same die size). The other two are Tyler's call, owed before S5 and S6 are built
  (`docs/UI-SPEC.md`): proposed `counters[]` ≤ 20, the same bound as `notes[]` — a comparable
  curated per-character list, and the point past which S6's own row budget stops being a
  quick-glance resource screen; proposed `spellcasting.spells[]` ≤ **60**, anchored to the two
  2014 ceilings that actually bind. A maximal prepared caster lands at exactly 40 — a
  20th-level cleric prepares level + WIS ≤ 20 + 5 = 25, plus 5 cantrips, plus 10 always-prepared
  domain spells (two at each of cleric 1/3/5/7/9) — so 40 is the ceiling with **zero** slack,
  not a bound with room above it. A wizard is larger still: a RAW spellbook is 6 spells at 1st
  level plus 2 per level thereafter = 44, plus 5 cantrips = **49**, before a single scroll is
  copied in. 60 clears the wizard by eleven and the cleric by twenty. If a bound is ever hit,
  S5 refuses the addition with a line of text rather than dropping anything — the same shape as
  S9's attunement cap — because silently discarding a transcribed spell is worse than a wall.
  The schema file itself is out of scope for this repair pass, so neither number is binding
  yet — both are recorded here as owed, not decided.
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

Events (the only way state changes in play): `damage`, `heal`, `temp`, `tempDelta`,
`deathSave`, `spendHitDie`, `shortRest`, `longRest`, `dawn`, `spendSlot`, `spendPactSlot`,
`counter`, plus edits from the wizard. Reference: `rules.py::apply_event`; fixtures:
`events.json`. `temp` and `tempDelta` are two different things and must stay separate:
`temp{amount}` is a **grant** (a spell or feature; keeps the higher number, never stacks),
`tempDelta{delta}` is a **correction** to the number already on the sheet (signed, clamped at
0, and it does stack). Only the correction can lower temp HP.

### Room mapping

```
characters(id TEXT PK, name TEXT, summary TEXT, updatedAt INTEGER, json TEXT)
```
One row per character; `json` is the schema document (kotlinx-serialization). `summary`
("Cleric 5 · Hill Dwarf") is denormalised for the Home list. Writes are debounced 400 ms inside
`CharacterRepository`, on its own process-lifetime scope rather than the view model's, and
wrapped in `withContext(NonCancellable)` — see `docs/ARCHITECTURE.md`'s persistence rule for
why (a screen pop cancels `viewModelScope` synchronously, before a `viewModelScope`-hosted
`delay(400)` could ever fire). The `characters(...)` columns above are **frozen at creation**:
character evolution goes entirely through `schemaVersion` + `Model.migrate(json)` inside the
`json` column, never a Room migration (`buildDatabase` offers none — the same shape ADR-0009
gives the compendium). A hot column added later — something the Home list needs to sort or
filter on without decoding every row's JSON — needs a **second file**, e.g.
`characters-v2.db`, not a migration.

**The second file is copied into, not started empty.** This is where the character file and the
compendium file part company: the compendium is disposable because a new `compendium-v<N>.db`
re-imports from the bundled assets, and a character file has no source to re-import from. So a
file bump reads every row out of the old database and re-inserts it into the new one — the
`json` column is the durable record and carries forward byte-for-byte, with `schemaVersion` +
`Model.migrate(json)` doing any content evolution and the new hot column derived from the
decoded document. The old file is deleted only **after** the copy commits, the character
analogue of `StaleDbFiles`; a bump that failed halfway leaves the old file intact and retries on
the next launch. Skipping the copy would delete the player's characters, which is the one thing
this schema exists to prevent.

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

`compendium/AssetImporter.kt` — pure behind the `AssetSource` / `ImportMarker` /
`CompendiumWriter`+`ImportSink` seams; run on the device by `CompendiumStore` from
`HomeViewModel.onScreenShow` (every show; the store's state is the guard):

```
index  = readAsset("compendium/index.json"), decoded strictly (schemaVersion 1, exactly the 22 kinds)
stamp  = "$SCHEMA_VERSION.$FORMAT:$bundleSha256"           -- "1.1:fce4d793…" today
Ready  iff DataStore["compendium.stamp"] == stamp
       AND COUNT(records)      == Σ index.files[*].count    -- 1 992
       AND COUNT(search_index) == Σ index.files[*].count
else, in ONE transaction (db.withTransaction):
  DELETE FROM records; DELETE FROM search_index
  for each Kind in S13 order (rules before rule_sections):
    readAsset("compendium/<kind>.json")  → size == index.files[kind].bytes
    → JsonArraySplit.elements (raw slices) → strict decode → both counts == index.files[kind].count
    → Rows.of(kind, position, slice, record, ctx) → INSERT records + search_index
    → progress tick (done / 22)
commit; only then DataStore["compendium.stamp"] = stamp
```
Any exception rolls the transaction back and leaves the stamp untouched; the next
`onScreenShow` retries (state `Failed(reason)` shows the reason on Home). There is no spinner
in `sdk:ui`: Home shows `Preparing the rules…` over a determinate `LightProgressBar` until
Ready, and `CompendiumStore.reader()` throws unless the state is Ready. Measured first launch
on the LP3: 2.3–2.5 s; a relaunch skips the import on the stamp-and-count path (ADR-0009;
method in `docs/sdk-facts-delta.md`).

Room shape (ADR-0009): **one** `records` table for all 22 kinds — primary key `(kind, key)`,
index `(kind, sortName)`, hot columns `position, level, school, castingTime, concentration,
ritual, classList, classKey, subclassKey, parentKey, category, subcategory, rarity, cr`, and a
last `json` column holding the record's raw asset slice (byte-identical to the bundle, never
re-encoded, never selected by list queries) — plus a standalone
`search_index(kind, key, name, body)` `@Fts4` table (`unicode61`; `kind`/`key` `notIndexed`;
body per kind from `Body.of`). The file is `compendium-v<SCHEMA_VERSION>.db`: a `RecordRow` /
`SearchRow` change bumps `SCHEMA_VERSION` (a new file that imports from scratch; `StaleDbFiles`
deletes the old one), never a Room migration — `buildDatabase` offers none. Characters live in
a separate `grimoire.db`; the journal gets its own `journal.db` (M5 — see the decision above).
Never ship a `.db` (builder rejects the extension); never mutate compendium tables.

## 3. Journal

Own database, `journal.db` (M5, ADR-0010 forthcoming) — kept out of `grimoire.db` per the
decision above. Six entities, one link primitive (see `journal.schema.json`):

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
