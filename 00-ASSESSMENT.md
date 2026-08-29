# 00 — Feasibility & Permissibility Assessment — Grimoire

Per-tool assessment: does this specific tool clear the SDK's technical bar and Light's
approval bar. Cross-tool prioritisation lives in light-workspace. Verified against
`lightphone/light-sdk` commit `3df3c24` (2026-08-25, `sdkVersion=0.1.1`); full evidence
in `docs/research/04-light-sdk-state.md`.

## Required capabilities

- Bundle ~2.6 MB of reference data (SRD 5.1 as JSON) and query it offline with search.
- Persist a handful of character documents and a journal; survive process death.
- Tap and scroll-wheel input; monochrome list/reader UI; occasional single-line text entry.
- Deterministic dice; small transient result overlay.
- No network, no background work, no notifications, no camera (v1).

## SDK surface verification

| Capability | Exposed? | Evidence |
|---|---|---|
| Read bundled assets | **Yes** — `SealedLightContext.readAsset(path): ByteArray` | `sdk/client/.../LightActivity.kt:229-244` |
| Asset format/size on Light's builder | `.json/.txt/.md/.bin/.dat/.csv/…`, ≤ 5 MiB/file, 100 MiB total; `.db/.gz` abort | `builder/lightbuilder/allowlist.py:17-21,46`, `extract.py:216-225` |
| SQLite via Room | **Yes** — `lightContext.buildDatabase(cls, name)`; `androidx.room` + `room-compiler` (KSP) allow-listed; no `createFromAsset` | `LightDb.kt`; `LightSdkPlugin.kt:17-45,73-75` |
| Key/value prefs | **Yes** — `lightContext.dataStore` | `LightActivity.kt:229-235` |
| JSON decoding | **Yes** — `kotlinx-serialization` allow-listed | `LightSdkPlugin.kt:28` |
| Scroll wheel / hardware keys | **Yes, to the tool's `onKeyDown`** — codes 317/318/319 enumerated; forwarding to LightOS for unconsumed keys | `LightKeyHandler.kt`, `LightActivity.kt:152-197`, light-keyboard `HardwareKeyboardInput.kt:18-35`. **Retail wheel-turn delivery unverified → M0 hardware test** |
| Text entry | **Yes** — `LightTextInputEditor` (full-screen, `singleLine`, `initialCaps`) | `LightTextInputEditor.kt:47-221` |
| Lists / reader / bars / icons | **Yes** — `LightScrollView`, `LightLazyScrollView` (uniform rows), `LightText`, `LightTopBar`/`LightBottomBar`, 106 icons | `sdk/ui/…` inventory in research 04 §C |
| Transient overlay | **Yes** — `LightModalManager.show(modal, duration)` | `LightModalManager.kt:25-95` |
| Haptics | Only the automatic click in `lightClickable` (fine) | `LightClickable.kt:17-48` |
| Clipboard / share / file import / notifications / exact alarms | **No** — none exposed; designed around (on-screen export, no alerts) | research 04 §D.3 |
| QR display (later) | `com.google.zxing:core` allow-listed; no permission to render | `LightSdkPlugin.kt:41` |
| QR scan / voice memo (later) | `CAMERA`, `RECORD_AUDIO` allow-listed; `LightQrCodeScanner`, `LightAudio` exist | `LightToolMetadata.kt:176-188`; `sdk/client/.../audio/*` |

## Permission allow-list check

`tool/lighttool.toml` requests `permissions = []`. Nothing to cross-check; the empty list is
valid (`permissions` is optional at HEAD, Aug 2026 — `LightToolMetadata.kt:120-121`; the
July snapshot still listed it as required). Future
additions (`CAMERA`, `RECORD_AUDIO`) are both in `ALLOWED_PERMISSIONS`.

## Third-party dependency allow-list check

Used: `androidx.compose.*`, `androidx.room:*` (+ `ksp(androidx.room:room-compiler)` — the
only allowed KSP processor), `org.jetbrains.kotlinx:kotlinx-serialization-json`,
`androidx.datastore:*`, `com.github.lightphone:light-keyboard` (JitPack), `androidx.lifecycle`,
`org.jetbrains.kotlinx:kotlinx-coroutines`. All on the 27-prefix allow-list. **No additions
requested.** `.java` sources are rejected — Kotlin only.

## Ethos argument

Light's bar: "matches the Light ethos both functionally and aesthetically"; "clear
intentional purpose"; "no … infinite feeds". Grimoire is opened to answer one question at
the table and put down: what do I roll, how many slots are left, what does *grappled* do.
It is finite by construction (fixed data, bounded trackers, a log that ends with the
session), silent (no notifications, no network), and built only from Light's own UI
primitives. It replaces an eraser and an index, not the table. Full one-pager:
`docs/VETTING-DEFENSE.md`. Category risk: none of the banned categories is adjacent; the
only reviewer question we anticipate — "is a game aid intentional?" — is answered by the
absence of any engagement mechanic and by the community precedent of offline reference
tools (Dictionary, three Bibles, Recall) and finite games (Chess, Sudoku) already listed.

## Verdict

**Go.** No blocker. Two items gated design, not feasibility, and both were settled in M0 (28 Aug 2026,
TLP301, LightOS 572-release-lp3): (1) wheel *turns* reach a tool on retail LightOS (317/318/319 —
`docs/sdk-facts-delta.md` §Hardware results), so the wheel design stands; (2) first-launch import of
2.6 MB into Room measured 3.1–3.3 s — under the 4 s bar, so the `.bin` prebuilt-SQLite fallback
(ADR-0002) is not scheduled.
