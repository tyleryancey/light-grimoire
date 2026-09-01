# Grimoire — Roadmap & milestones (checkbox plan of record)

Conventions: each milestone ends in a checkable "done" state; the pure-JVM gate is green
before any UI; commit per task; `CLAUDE.md` mirrors this list and is the handoff doc.

## M0 — Verify, don't trust (½ day)

- [x] Scaffold the repo with `new-light-tool` (`light-grimoire`, id `dev.tyler.grimoire`), apply this overlay, first PR green (`check`, `submission-check`).
- [x] `./gradlew :tool:assembleDebug` on the pristine scaffold with the compendium assets in place (proves the builder-style asset set builds and the plugin scan passes).
- [x] Install `ui-demo` on the physical LP3 (LightOS ≥ v572) and record which key codes arrive on the **Key Events** screen for wheel up / wheel down / wheel press / volume / camera. Write the result into `docs/sdk-facts-delta.md` §Hardware. **This decides the wheel design.** → Done 28 Aug 2026 on LightOS 572-release-lp3: wheel turns reach the tool (317 toward the top of the phone / 318 toward the bottom / 319 press); volume (24/25) and camera (80 focus / 27 shutter) too. Wheel design stands.
- [x] Measure first-launch import time for 2.6 MB of JSON into Room on the LP3 (a throwaway spike screen). If > 4 s, plan the `.bin` prebuilt-SQLite fallback (ADR-0002). → Measured 28 Aug 2026 (`spike/import-timing`): 3.13–3.25 s for 1 992 rows + FTS4 (decode 2.4–2.5 s, insert 0.7 s). Under the bar; JSON→Room stands.
- [x] `python3 -m pipeline all && git diff --exit-code tool/src/main/assets fixtures` is empty on a clean clone.

## M1 — Pure core (the oracle in Kotlin) (2–3 days)

- [x] `rules/Model.kt` — `Character` + nested types with kotlinx-serialization; round-trips `fixtures/characters/*.json` byte-for-byte after normalisation (normalisation = drop null-valued keys and compare as JSON trees; the hand-written samples omit some optional keys and spell others as `null` — see `ModelRoundTripTest`).
- [x] `rules/Dice.kt` — Mulberry32 (bit-exact: `fixtures/rng.json`), parser, `roll`, `withAdvantage`, `bounds`, `average` (`fixtures/dice.json`, invalid list rejected).
- [x] `rules/Tables.kt` + `rules/Derive.kt` — `fixtures/math.json`, `slots.json`, `derived.json` green.
- [x] `rules/Ledger.kt` — every scenario in `fixtures/events.json` green, error cases throw.
- [x] Property tests: HP never negative, temp never stacks, long rest never exceeds max, counters clamp.
- [x] `./gradlew :tool:testDebugUnitTest` green; no `android`/`androidx` import under `rules/` (hook enforces). → Done 29 Aug 2026 (`feat/m1-rules-core`): 58 JVM tests in 11 classes replay all six fixtures plus the compendium cross-check; property tests were mutation-checked; two oracle gaps found in review were fixed fixture-first (dice constant cap, `spend_slot` level bounds) and the Python staleness guard now covers `derived.json`/`events.json`.

## M2 — Compendium on device (2 days)

- [x] Room entities/DAOs ~~per kind~~ + `search_index` FTS4; `AssetImporter` keyed by `index.json` bundle hash. → Done 29 Aug 2026 (`feat/m2-compendium-db`): one `records` table + FTS4 (ADR-0009), 2.3–2.5 s first launch on the LP3, 169 JVM tests
- [x] Compendium screens: kind list → filtered lists (spells by level via wheel) → reader (S10) with cross-links; search via editor screen (S13). → Done 31 Aug 2026 (`feat/m2-compendium-screens`): S13 hub, the per-kind lists and spells-by-level (the wheel steps the level), the S10 reader with chained cross-links, and search that returns name matches before mentions (`20b3995`, `5777b46`, `cf5b0a8`, `5d83ddc`).
- [x] About screen renders `assets/legal/ATTRIBUTION.md`. → Done 31 Aug 2026 (`feat/m2-compendium-screens`): S16 draws the generated attribution verbatim through the shared Markdown renderer (`ui/common/MarkdownBlocks`, also S10's), with `BuildConfig`'s version and tool id beside it; S0 Home gets the `COMPENDIUM` row in its Ready branch and an `ABOUT` bottom-bar button reachable in every import state, so the CC-BY text can be read even when the import fails. 330 JVM tests in 36 classes.
- [x] ~~Emulator~~ **Hardware** QA: every kind opens; "fire" finds Fireball; a 1,500-word rule section scrolls with the wheel (or touch if wheel is unavailable). → Done 31 Aug 2026 on the LP3 (TLP301, build 00WW_1_440000, USB, `serverPackage` left at `com.lightos` — no emulator flip needed). Run on hardware rather than the AVD because the wheel is the thing worth testing and the emulator emits no 317–319; `adb shell input keyevent 317/318/319` does reach the tool on the device. First launch imported in 2 290 ms; a force-stop relaunch logged `compendium ready rows=1992` with no import line and `compendium-v1.db` at 6 279 168 B. Verified: hub counts 319/15/237/362/334; FIND "fire" → **Fireball first**; the S10 reader's header lines and bold run-ins; the wheel scrolling the 2 997-word Traps section with the tool **staying foreground** (the consume policy holds through both halves of each detent); the leading duplicate heading dropped; Solar's ability grid — the bundle's widest at exactly the 48-character budget — column-aligned and unclipped; the two-tier search drawing `ALSO MENTIONED` with a kind on each row; spells-by-level stepping cantrips → level 3 on the wheel with the stepper arrow hidden at the clamp; a header-only class page with its SUBCLASSES and FEATURES link rows; About rendering the whole attribution and `Grimoire 0.1.0 (1)`.

## M3 — Sheet & trackers (4–5 days)

- [ ] Home (S0), Sheet hub (S1), HP pad (S3) with death saves, Checks & saves (S4), Conditions (S7), Features & resources (S6).
- [ ] Spells (S5) with slot pips, prepare mode, cast → spend (upcast chooser), concentration line.
- [ ] Turn (S2): rows from attacks + prepared spells + `showOnTurn` counters; one-tap attack+damage; long-press chooser; roll modal (S11).
- [ ] Rest (S8): short rest with hit-dice spend (roll/average), long rest with confirm + summary.
- [ ] Gear & coin (S9).
- [ ] Persistence: debounced + NonCancellable saves; kill-and-relaunch test on every screen.

## M4 — Creation from paper (2 days)

- [ ] Wizard (S12) steps 1–9; class-table counters and hit dice seeded from the compendium; custom class/subclass/spell/attack paths.
- [ ] Edit from S1 re-enters any step; delete with confirm.
- [ ] Transcribe the three fixture characters from their "paper" (printed JSON) in ≤ 4 min each with ≤ 2 keyboard trips (the success criterion).

## M5 — Dice & journal (2–3 days)

- [ ] Dice (S15) with bounded history.
- [ ] Journal (S14): sessions, capture flow (kind → roster → links → one-liner), rosters with derived mentions, quest status, party gold, text-page export.
- [ ] Journal JSON round-trips `pipeline/schema/journal.schema.json`.

## M6 — Polish, QA, submission (2 days)

- [ ] Monochrome audit: no `Color(` literal in `ui/`; weight/glyph review of every state.
- [ ] Finite-by-rule audit: every list bounded, no infinite scroll, no streaks/stats.
- [ ] `./gradlew :tool:assembleRelease` (R8) passes; APK size recorded; release runs on hardware.
- [ ] `README.md` in Tyler's own words + the vetting one-pager; screenshots via the awesome-light hero formatter; `SUBMISSION.md` current; `awesome-light` PR.
- [ ] Post a design-intent note in `#developers` (human-written) before public release.
- [ ] Tag `v<versionName>` with `release-tool` (`v0.1.0` for the first public build; `1.0.0` when submission-ready).

## Later (not scheduled)

- QR export/import (zxing encode needs no permission; scan needs `CAMERA` — re-run the vetting one-pager).
- Quick build from SRD picks; level-up assistant.
- Voice memo on journal entries (`RECORD_AUDIO`, `LightAudio`).
- SRD 5.2.1 edition pack (schema already carries `edition`; reference engine grows a `2024` branch).
- File-based import/export when the LightOS File Manager exposes a per-tool inbox.
