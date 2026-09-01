# CLAUDE.md — Grimoire (Light Phone 3 tool)

A 5E-compatible player companion: the mutable half of a paper character sheet (HP, slots,
uses, conditions, coin), one-tap dice for the rolls a turn needs, the SRD 5.1 rules text a
player looks up mid-session, and a wheel-driven session journal. Offline, `permissions = []`,
finite by construction.

**Division of labor:** this doc is the plan of record; Claude Code owns compile–run–debug.
SDK source outranks this doc. The Python reference engine outranks the Kotlin one.

Specs: `docs/PRD.md` (scope) · `docs/UI-SPEC.md` (screens) · `docs/DATA-MODEL.md` ·
`docs/ARCHITECTURE.md` · `docs/VETTING-DEFENSE.md` · `docs/LICENSING.md` · `docs/adr/` ·
research in `docs/research/`. Skills in `.claude/skills/` carry the domain knowledge; run
`/milestone` to start a session.

## Purpose

For a player at a physical table who keeps a paper sheet. The paper stays the record of the
static character; the phone owns what pencil-and-eraser and the book handle badly. No tabletop
aid is listed on awesome-light or GitHub as of 29 Aug 2026 (Reddit unchecked).

## Verified SDK facts this tool relies on (`lightphone/light-sdk` @ `3df3c24`, 2026-08-25)

- Assets: builder extracts only `tool/`; allowed extensions `.json .txt .md .bin .dat .csv …`;
  **5 MiB per file**; disallowed extension aborts (`builder/lightbuilder/allowlist.py`). →
  compendium = JSON chunks → Room on first launch (ADR-0002).
- `SealedLightContext`: `dataStore`, `filesDir`, `readAsset(path)`, `buildDatabase(cls, name)`;
  no `Context`, clipboard, share, file picker, notifications, exact alarms, custom haptics.
- Allow-listed deps used: Compose, Room (+ `room-compiler` via KSP), kotlinx-serialization,
  DataStore, light-keyboard (JitPack). `minSdk 34`, JDK 17.
- Hardware keys reach `LightKeyHandler.onKeyDown` on the current screen/VM; LP3 wheel =
  **317 up / 318 down / 319 press** (`LightDeviceKeys`); unconsumed keys go to LightOS,
  which relaunches the tool (→ `onScreenShow` again). Wheel *turns* verified on retail
  LightOS 572 (28 Aug 2026): 317 = toward the top of the phone, 318 = toward the bottom, 319 = press,
  one DOWN/UP pair per detent; volume (24/25) and camera (80/27) also reach a consuming tool.
- `LightTextInputEditor` (full-screen, `singleLine`, `initialCaps`) is the only text input;
  `LightTextField` is display-only; `LightLazyScrollView` needs uniform rows;
  `LightModalManager.show(modal, 2 s)` for transient results; no toggle/stepper/tabs/dialog.
- `lighttool.toml`: 5 required fields; `permissions` optional; `orientation = "portrait"` OK.
- Corrections to the July snapshot: `docs/sdk-facts-delta.md` (minSdk 34, no GitHub
  Packages token, kotlinx-datetime allowed, back bar not drawn by SDK, …).

## lighttool.toml

```toml
[tool]
id            = "dev.tyler.grimoire"
label         = "Grimoire"
versionCode   = 1
versionName   = "0.1.0"
permissions   = []
orientation   = "portrait"
serverPackage = "com.lightos"
```

## Architecture (one line per package — see `docs/ARCHITECTURE.md`)

- `rules/` — PURE oracle mirror: `Dice`, `Tables`, `Derive`, `Ledger`, `Model`; replays `fixtures/*.json`.
- `compendium/` — Room entities/DAOs for the 22 bundled kinds + `AssetImporter` + FTS4 search.
- `data/` — `CharacterRepository`, `JournalRepository`, `Prefs` (DataStore), codecs, ids.
- `journal/` — journal model + Markdown/JSON export renderers.
- `ui/` — screens per `docs/UI-SPEC.md` (S0–S16) built only from `sdk:ui`; `ui/keys` maps the wheel.
- `pipeline/` (desktop) — compendium build, reference engine, fixtures, `compendium` MCP server.

## Behavior

Screens S0–S16 in `docs/UI-SPEC.md`. One-tap contract: attack+damage, HP change, cast,
condition toggle, inspiration, death save, rest start. Reading a spell ≤ 2 taps. Keyboard
only for names. Everything bounded (characters ≤ 6, attacks ≤ 12, items ≤ 60, search ≤ 50,
dice history 10).

## Milestones · definitions of done

Checkbox plan of record lives in `docs/ROADMAP.md` (M0 verify → M1 pure core → M2
compendium → M3 sheet & trackers → M4 creation from paper → M5 dice & journal → M6 QA &
submission). Tick boxes there; mirror the current milestone here:

- [x] **M0** — scaffold via `new-light-tool`, first PR green, `assembleDebug` with assets,
      wheel key codes recorded on hardware, first-launch import timed, pipeline reproducible.
- [x] **M1** — `rules/` replays every fixture; JVM gate green (29 Aug 2026: 58 JVM tests in
      11 classes, property tests mutation-checked, `assembleDebug` clean).
- [ ] **M2** — compendium on device: Room entities + `AssetImporter`, kind/list/reader/search
      screens. (task 1 — data layer — done 29 Aug 2026; task 2 — hub, lists, S10 reader, S13
      search — done 31 Aug 2026; task 3 — S16 About + Home wiring — done 31 Aug 2026, 330 JVM
      tests in 36 classes. Left: emulator QA.)

## Commands

```
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew :tool:testDebugUnitTest      # pure-JVM gate — green before any UI work
./gradlew :tool:assembleDebug          # plugin scan + APK
./gradlew :tool:clean                  # separate invocation, never combined
./gradlew :tool:assembleRelease        # what Light's builder runs (R8) — before submitting
python3 -m pipeline all && python3 -m pytest pipeline/tests -q   # data + oracle
```
Emulator/device loop: `run-light-tool` skill (light-workspace). Release: `release-tool`.

## Working rules

- Fixture-first for rules: change `pipeline/reference/` → regenerate → port to Kotlin
  (`test-oracle` agent). Never weaken a fixture to make Kotlin pass.
- Verify-don't-trust for the SDK: `/sdk-fact <claim>` before asserting; record in
  `docs/sdk-facts-delta.md`.
- Review before commit: `lp3-code-reviewer` agent. Vet before release: `/vet`.
- Generated files are never hand-edited (hook blocks it): assets, legal, fixtures.
- Monochrome, finite, calm: no colour literals, no Material widgets, no infinite lists, no
  network, no notifications, settings near-empty.
- Commit per task; PRs merge with `--merge` (never squash/rebase); `serverPackage` stays
  `com.lightos` in commits; every `gh` call names `-R tyleryancey/light-grimoire`.
- All text sent to `lightphone/*` or the community is written by Tyler, in his own words.

## Vetting defense (seed)

Offline, `permissions = []`, finite by construction, native `sdk:ui`, MIT code + CC-BY-4.0
SRD 5.1 data with the prescribed attribution, "5E compatible" and no WotC marks. Full
one-pager: `docs/VETTING-DEFENSE.md` (kept current; copied into `README.md`).

## Decisions (ADRs) and open questions

ADR-0001 SRD 5.1 first · 0002 JSON→Room · 0003 counters · 0004 zero permissions · 0005 name
"Grimoire" (confirmed 28 Aug 2026; `id` permanent) · 0006 mulberry32 dice ·
0007 paper-first transcription · 0008 journal shape · 0009 one `records` table + FTS4,
file-name versioning instead of migrations.
Open: creatures in v1; journal v1 vs v1.1; roll history;
`.db` assets ever allowed; Tool Library submission mechanics ("early Fall").

## Sharp edges

- The plugin scan walks all of `tool/src` incl. tests; a banned token in a **string or
  trailing comment** fails the build — only a line starting with `//` is exempt.
- `clean` and `assemble*` must be separate Gradle invocations (generated manifest).
- `LightTextInputEditor`'s text area does not scroll — keep entries single-line/short.
- Popping a screen cancels `viewModelScope` synchronously — `withContext(NonCancellable)`
  around every save; merge related writes into one coroutine.
- `onScreenShow` fires on `onResume` too (volume/wheel modal relaunch) — guard refreshes.
- `versionName` is strict `x.y.z`; `versionCode` must increase per release; keep this
  file's toml block byte-identical to `tool/lighttool.toml`.
- No GitHub Packages token is needed anymore (keyboard via JitPack) — ignore older docs.
- First-launch compendium import measured 2.3–2.5 s on the LP3 (29 Aug 2026: 2 543 / 2 281 /
  2 268 ms; decode ≈ 1.47 s, insert ≈ 0.56 s — the store's `compendium import …` logcat line,
  method in `docs/sdk-facts-delta.md`) for 1 992 rows + FTS4; a relaunch skips the import via
  the stamp-and-count path; `compendium-v1.db` is 6.3 MB on device (+ a WAL of the same size
  until checkpoint).
- `sdk:ui` has no spinner — the wait is `Preparing the rules…` over a determinate `LightProgressBar`.
- Any `RecordRow`/`SearchRow` change needs a `CompendiumDb.SCHEMA_VERSION` bump (a new
  `compendium-v<N>.db` file; stale files deleted) — never a Room migration: `buildDatabase` has none,
  and a forgotten bump throws "Room cannot verify the data integrity… forgot to update the version
  number" at open on every installed phone; `StaleDbFilesTest` pins the column set beside the version.
- `CompendiumStore.reader()` throws unless the state is Ready — navigate off Home only after Ready.
- `ui/common/MarkdownBlocks` draws S10's compendium prose **and** S16's CC-BY attribution: never give a
  prose branch a `maxLines` — the licence sentence would be clipped on device with the JVM gate green.
  The whole truncation policy is `maxLinesOf` (only `Block.Mono` clips); `AboutViewModelTest` pins it.
- A first launch while the phone is dozing (screen off) is slow (11.5 s measured, throttled CPU)
  but still completes — do not add a timeout.
