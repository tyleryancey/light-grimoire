# ADR-0002 — Ship the compendium as JSON chunks and import into Room on first launch

**Status:** accepted · **Verified against:** `light-sdk` `builder/lightbuilder/allowlist.py`, `extract.py`, `LightDb.kt`

## Context
Light's server builder only extracts assets with an allow-listed extension (`.json .txt .md
.bin .dat .csv …`), caps files at 5 MiB, and aborts on anything else. `SealedLightContext.
buildDatabase` wraps `Room.databaseBuilder(...).build()` with no `createFromAsset`, and
tools cannot obtain a `Context` to open a database file themselves via Room.

## Decision
`pipeline/emit.py` writes one JSON file per kind (largest: creatures 0.77 MB) plus
`index.json` with a bundle hash. On first launch (or when the hash changes) the tool decodes
the chunks with kotlinx-serialization and bulk-inserts into Room inside one transaction,
then builds an FTS4 search table. The hash is recorded in DataStore.

## Alternatives
- Prebuilt SQLite shipped as `compendium.bin`, copied to `filesDir`, opened read-only with
  `android.database.sqlite.SQLiteDatabase` (not a blocked import). Kept as the fallback if
  the first-launch import measures > ~4 s on the LP3 (M0 task). Name it honestly in the
  submission if used.
- Search in memory without Room: simpler, but 2.6 MB of decoded objects resident for the
  whole session is unnecessary on a 6 GB phone that kills background processes freely.

## Consequences
The importer is a one-time cost behind a spinner; compendium tables are immutable; the
build stays reproducible (`git diff --exit-code` on assets).

Measured on the LP3 (TLP301, LightOS 572-release-lp3, 28 Aug 2026; branch `spike/import-timing`):
first launch 3 254 / 3 183 / 3 125 ms for 1 992 rows plus an FTS4 table — decode ≈ 2.45 s,
insert + FTS ≈ 0.7 s. Under the 4 s bar: the JSON→Room decision stands; the prebuilt-SQLite
fallback stays unscheduled. Decode dominates, so typed `@Serializable` models (not `JsonElement`)
are the first lever if the spinner ever feels long.

**Addendum, 29 Aug 2026 (`feat/m2-compendium-db`; schema and versioning in ADR-0009).** The
importer as built uses one generic `records` table plus a standalone `search_index` FTS4 table
(not one table per kind), strict typed models decoding the raw JSON slices, a DataStore stamp
(`compendium.stamp` = `"$SCHEMA_VERSION.$FORMAT:$bundleSha256"`) **and** row counts in both
tables as the Ready gate, and a determinate `LightProgressBar` — `sdk:ui` has no spinner.
Measured on the LP3 (TLP301, LightOS 572-release-lp3, phone awake, `pm clear` between runs):
three true first-launch imports **2 543 / 2 281 / 2 268 ms** total — decode 1 482 / 1 471 /
1 468 ms, insert 571 / 562 / 558 ms — for 1 992 records plus the FTS4 table (the store's own
logcat line; method and provenance in `docs/sdk-facts-delta.md`); a relaunch takes the
stamp-and-count path (`compendium ready rows=1992`, no import line). On device
`compendium-v1.db` is 6 279 168 bytes plus a WAL of the same size until checkpoint. A first
launch while the phone was dozing (screen off) logged 11 460 ms on a throttled CPU and still
ended Ready. The `.bin` fallback stays unscheduled.
