# ADR-0009 — One generic `records` table + standalone FTS4; file-name versioning instead of migrations

**Status:** accepted (decided with Tyler, 29 Aug 2026; implemented on `feat/m2-compendium-db`) ·
**Verified against:** `light-sdk` @ `3df3c24` `sdk/client/.../LightDb.kt:6-8`, Room 2.7.0 artifacts,
the bundle under `tool/src/main/assets/compendium/` (`index.json` `bundleSha256` `fce4d793…`,
1 992 records in 22 files) · **Supersedes** the per-kind-table wording in ADR-0002 and
`docs/DATA-MODEL.md` §2.

## Context

ADR-0002 ships the compendium as 22 JSON chunks and imports them into Room on first launch.
It left the Room shape open ("one table per kind with a `json` column and the hot columns the
lists need") and assumed a spinner. Facts that decided the shape:

- 1 992 immutable rows across 22 kinds; the lists need the same handful of hot columns
  (`level`, `school`, `category`, `subcategory`, `rarity`, `cr`) for every kind.
- `SealedLightContext.buildDatabase(dbClass, dbName)` is `Room.databaseBuilder(...).build()` with
  no `addMigrations`, no `fallbackToDestructiveMigration`, no `createFromAsset`
  (`LightDb.kt:6-8`): a Room `version` bump would throw at open with nothing to catch it.
- `sdk:ui` has no spinner — `LightProgressBar(colors, progress: Float)` is the only progress
  component (`LightProgressBar.kt:27`), so the wait has to be determinate.
- Room, FTS and DataStore cannot run in JVM unit tests under the plugin's dependency allow-list.

## Decision

1. **One generic `records` table** (`RecordRow`: primary key `(kind, key)`, index
   `(kind, sortName)`, the hot columns above plus `position`, `castingTime`, `concentration`,
   `ritual`, `classList`, `classKey`, `subclassKey`, `parentKey`) **and one standalone
   `search_index` FTS4 table** (`SearchRow(kind, key, name, body)`, tokenizer `unicode61`,
   `kind`/`key` `notIndexed` so a `MATCH` can be kind-scoped). Two entities, one DAO
   (`CompendiumDao`), one `@Database` (`CompendiumDb`). Every list query is kind-scoped, bounded
   and returns the `CompendiumRef` projection; only `get`/`getAll` select `json`.
2. **Raw-slice `json` column.** A pure JSON-array splitter (`JsonArraySplit`) cuts each asset
   file into its element slices; the slice is stored byte-identical to the bundle, never
   re-encoded. The same strict models decode it at read time.
3. **Strict typed decode** (`CompendiumJson`: `ignoreUnknownKeys = false`,
   `explicitNulls = false`): 18 `@Serializable` records (five prose-only kinds share
   `TextRecord`). A field the pipeline emits that the model lacks fails the JVM gate
   (`RecordsDecodeTest`) — the model gains the field; the decoder is never loosened.
4. **Ready gate = stamp AND counts.** DataStore key `compendium.stamp` holds
   `"$SCHEMA_VERSION.$FORMAT:$bundleSha256"` (`"1.1:fce4d793…"` today), written only after the
   import transaction commits. The database is Ready when the stamp matches **and**
   `COUNT(*)` of `records` **and** of `search_index` both equal `Σ index.files[*].count`
   (1 992). The stamp alone lies after a lost file; a count alone lies after an equal-count
   bundle or a short FTS table. No on-device hashing.
5. **One transaction per import** (`withTransaction`): clear both tables, then per kind in
   `Kind` order (`rules` before `rule_sections`, whose chapter is resolved from `rules.json`):
   read → size-check against the index → split → strict decode → count-check → derive rows →
   insert → progress tick (22 ticks). Any exception rolls the transaction back and leaves the
   stamp untouched; the next `onScreenShow` retries.
6. **File-name versioning.** The database file is `compendium-v<SCHEMA_VERSION>.db`; Room's
   own `version` stays 1 for good. A change to `RecordRow` or `SearchRow` is a
   `SCHEMA_VERSION` bump — a new file that imports from scratch — and `StaleDbFiles` deletes
   the older `compendium-v*.db{,-wal,-shm,-journal}` best-effort under
   `filesDir.parentFile/databases`. `FORMAT` (in `AssetImporter`) bumps when row derivation
   changes without a schema change: same file, re-import.
7. **User data lives elsewhere.** Characters and the journal (M3+) go in a separate
   `grimoire.db`; the compendium file is disposable, the character file is not.

## Alternatives considered

- **One table per kind** (ADR-0002's sketch): 22 entities, 22 DAOs, per-kind FTS content
  tables with sync triggers, and a Room schema that changes whenever one kind's typed fields
  do. Judged over-built for 1 992 read-only rows that share one list projection.
- **Import-contract-first** (write the importer against DAO fakes, decide the schema later):
  rejected because the strict models and the raw-slice column are what make the JVM gate
  prove the bundle end-to-end; the schema had to be fixed first.
- **Room migrations**: not reachable through `buildDatabase`; and a destructive
  fallback on the compendium would also be the wrong tool for the character store.

## Consequences

- **Any `RecordRow`/`SearchRow` change needs a `SCHEMA_VERSION` bump** — never a migration.
  Forgetting it leaves Room's `version` at 1 over a file whose identity hash no longer matches,
  and every installed phone throws "Room cannot verify the data integrity. Looks like you've
  changed schema but forgot to update the version number…" at open (`RoomOpenHelper` /
  `BaseRoomConnectionManager`, room-runtime-android 2.7.0). The JVM gate pins the column set
  beside the version (`StaleDbFilesTest.aColumnChangeInEitherRowRequiresASchemaVersionBump`);
  reviewer rule, CLAUDE.md sharp edge.
- The pure layer — records, index, splitter, `Rows.of`, `Search`, `RulesBridge`,
  `AssetImporter` behind `AssetSource`/`ImportMarker`/`CompendiumWriter`+`ImportSink`,
  `ImportGate`, `StaleDbFiles` — is JVM-tested over the real bundle (169 tests in 23 classes
  at the time of writing). Only the DAO's SQL, FTS `MATCH`, `withTransaction` and DataStore
  run on the device; KSP verifies every `@Query` at `assembleDebug`.
- Home shows `Preparing the rules…` and a determinate `LightProgressBar` until Ready;
  `CompendiumStore.reader()` throws unless the state is Ready, so no screen can read a
  half-filled table.
- Measured on the Light Phone III (TLP301, LightOS 572-release-lp3, 29 Aug 2026, phone
  awake, `pm clear` between runs; the figures are `CompendiumStore`'s own
  `compendium import rows=… decode=…ms insert=…ms total=…ms` line read over
  `adb logcat -s Grimoire`, the file size from `run-as dev.tyler.grimoire ls -l databases/`;
  rounded in commit `71617b3`, method in `docs/sdk-facts-delta.md`): three true first-launch
  imports **2 543 / 2 281 / 2 268 ms** total (decode 1 482 / 1 471 / 1 468 ms, insert
  571 / 562 / 558 ms) for 1 992 records plus the FTS4 table — down from the M0 spike's
  3.1–3.3 s (typed decode was the lever). A relaunch takes the stamp-and-count path: logcat
  shows `compendium ready rows=1992` and no import line (not timed). On-device
  `compendium-v1.db` is 6 279 168 bytes plus a WAL of the same size until checkpoint (the
  plan estimated ≈ 4 MB). The very first run of the session, while the phone was dozing
  (screen off), logged `total=11460ms` on a throttled CPU and still ended Ready; it is
  excluded from the three.
- The `.bin` prebuilt-SQLite fallback of ADR-0002 stays unscheduled.
