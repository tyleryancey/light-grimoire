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
