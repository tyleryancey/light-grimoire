# SDK facts — corrections and additions vs the July 2026 snapshot

Verified against `lightphone/light-sdk` commit `3df3c24a…` (2026-08-25, `sdkVersion=0.1.1`)
and `lightphone/light-keyboard` `v0.0.18`. Full evidence: `docs/research/04-light-sdk-state.md`.
When you confirm or refute something on hardware, add a dated line here **and** correct
`docs/research/04-light-sdk-state.md` if the fact came from there.

## Now WRONG in the July snapshot

| July fact | Now | Evidence |
|---|---|---|
| `minSdk = 33` | **34** | `build.gradle.kts:14` |
| GitHub Packages creds (`gpr.user`/`gpr.key`, `GH_PACKAGES_*`) required for the keyboard | **Not required** — keyboard resolves from JitPack `com.github.lightphone:light-keyboard:v0.0.18` | `settings.gradle.kts:9-17`, `libs.versions.toml:60`, commit `6a7cbb6` |
| `kotlinx-datetime` not allowed | **Allowed** (`org.jetbrains.kotlinx:kotlinx-datetime`) | `LightSdkPlugin.kt:30`, commit `c4a502c` |
| all six `lighttool.toml` fields required | **5 required**; `permissions` optional (default `[]`); new optional `capabilities` (`["detached-audio"]` only) and `orientation` (`"portrait"` only) | `LightToolMetadata.kt:56-65,111-152` |
| 19 allow-listed dependency prefixes | **27** (+ datetime, light-keyboard, media3, bouncycastle, zxing:core, sol4k ×3) | `LightSdkPlugin.kt:17-45` |
| plugin version `0.0.12` | plugin inherits `sdkVersion=0.1.1` | `plugin/build.gradle.kts:8-13` |
| zero `@InitialScreen` fails the KSP build | zero **compiles and crashes at launch** (`No class annotated with @InitialScreen found`); >1 fails KSP | `LightSdkProcessor.kt:42-48`, `LightActivity.kt:105-106` |
| exactly one `@EntryPoint` required | **optional** (0 or 1); new `enablePushNotifications`, `enableRecentsScreenshots` | `LightSdkProcessor.kt:51-64`, `EntryPoint.kt:12-28` |
| override `LightViewModel.onBackPressed` handles back | only consulted on tool-initiated `goBack()`; Activity-level back bypasses it; the SDK draws **no** back bar — draw BACK yourself | `LightScreen.kt:89-93`, `LightActivity.kt:73-146` |

## New since July (relevant here)

- **Hardware keys**: `LightKeyHandler` on screens/VMs gets first refusal on every non-BACK/HOME
  key; unconsumed LP3 keys are forwarded to LightOS (`DeviceKeyEvent`) which foregrounds
  itself then relaunches the tool (so an unconsumed wheel event costs an `onAppPause` →
  `onScreenShow` round trip — the Weather VM's `skipRefreshOnNextScreenShow` guard). Wheel
  key codes: **317 up, 318 down, 319 press** (`LightDeviceKeys`). LightOS v572 "fixes all
  hardware button events for external tools". **Verified 2026-08-28 on LightOS 572-release-lp3:** wheel
  turns reach a consuming tool — see §Hardware results below.
- **Builder** (`builder/`): extracts only `tool/build.gradle.kts`, `tool/lighttool.toml`,
  `tool/src/main/{kotlin,java,res,assets}`; asset extensions allow-list
  `.png .jpg .jpeg .webp .gif .svg .json .txt .md .ttf .otf .bin .dat .csv .html .css`;
  **5 MiB per file**, 100 MiB total, 10k files; disallowed extension **aborts**; builds
  `:tool:assembleRelease --offline -DlightSdk.unsigned=true` with R8 minify + shrink.
- `SealedLightContext.readAsset(path): ByteArray` and `connectivity`; `buildDatabase` has
  no `createFromAsset` path.
- `LightTextInputEditor` gained `submitLabel`, `submitIcon`, `showBackButton`, `singleLine`,
  `initialCaps`, `editorKey`; the text area still does not scroll.
- `LightModal` / `LightModalManager.show(modal, 2 s)` transient overlays; `LightProgressBar`
  and `LightTouchableProgressBar`; `LightNfcTapReader`; 106 `LightIcons` (no dice/heart/d20).
- `LightAudio` (player/recorder/capture/voice), `detached-audio` capability,
  `LightServiceMethod.OpenDialer`, `GetUserPreferences(hapticsEnabled)`.
- `onResume` re-fires `willShow`/`onScreenShow` (not only navigation).

## Still true (re-verified)

Blocked imports (16) and code patterns (29); build-script bans; scan walks all of
`tool/src` incl. tests, per-line, comment-*line* exemption only; `.java` rejected; deps
validated declared + resolved; `ALLOWED_PERMISSIONS` (11) unchanged; KSP only
`room-compiler`; `LightGrid` 27×31; theme = `background/content/contentSecondary`;
`LightTextField` read-only; `LightFullscreenModal(message, onClose)` only; `LightWork`
15-minute floor; `clean` must be a separate Gradle invocation.

## Verified 29 Aug 2026 — compendium data layer (M2 task 1, `feat/m2-compendium-db`)

- **No spinner in `sdk:ui`.** `LightProgressBar(colors: LightColors, progress: Float)`
  (`sdk/ui/src/main/kotlin/com/thelightphone/sdk/ui/LightProgressBar.kt:27`) is the only progress
  component; `LightTouchableProgressBar` (`:45`) is its draggable variant. The one indeterminate
  indicator in the module is Material3's `CircularProgressIndicator` inside
  `LightQrCodeScanner.kt:140` — internal to that scanner, not an exported component, and Material
  widgets are off-limits here anyway. → Home shows `Preparing the rules…` + a determinate bar.
- **`buildDatabase` is a bare build.** `fun <T : RoomDatabase> SealedLightContext.buildDatabase(
  dbClass: Class<T>, dbName: String?): T = Room.databaseBuilder(androidContext.applicationContext,
  dbClass, dbName).build()` (`sdk/client/src/main/kotlin/com/thelightphone/sdk/LightDb.kt:6-8`) —
  no `addMigrations`, no `fallbackToDestructiveMigration`, no `createFromAsset`, no callbacks or
  options. A Room `version` bump throws at open with nothing to catch it; the compendium versions
  its *file name* instead (`compendium-v<SCHEMA_VERSION>.db`, ADR-0009).
- **`withTransaction` lives in `room-runtime-android` 2.7.0.** `androidx/room/RoomDatabaseKt.class`
  in `room-runtime-release.aar`'s `classes.jar` exports `withTransaction` and
  `withTransactionContext` (javap). `room-ktx-2.7.0.aar`'s `classes.jar` holds only
  `META-INF/androidx.room_room-ktx.version` — an empty shim kept for the coordinate. `@Fts4` /
  `FtsOptions` are in `room-common-jvm-2.7.0.jar`.
- **Room, FTS and DataStore cannot run in JVM unit tests.** `ALLOWED_DEPENDENCIES`
  (`plugin/src/main/kotlin/com/thelightphone/plugin/LightSdkPlugin.kt:17-45`) has no Robolectric,
  sqlite-jdbc or `room-testing`, and `validateDeclaredDependencies` (`:422`) walks every
  declarable configuration (`isCanBeDeclared`, `:426`) — `testImplementation` included — so nothing
  can be slipped in for tests. → the pure layer is tested through seams and DAO fakes; the DAO's
  SQL, FTS `MATCH`, `withTransaction` and DataStore are device-only checks (`adb logcat -s Grimoire`).
- **Plugin-scan traps for Room code.** `validateSourceFiles` (`:366-383`) walks all of `tool/src`
  incl. `src/test`, per line; `findSourceLineViolations` (`:184`) exempts only statements starting
  `//`, `*` or `/*` (`:197`). `\b\.java\s*\.\s*\w` (`:119`), `\b\.javaClass\b` (`:118`) and
  `Class.forName(` (`:120`) are banned in any statement — strings and trailing comments included —
  and `import kotlin.reflect.` is a blocked import (`:93`). `CompendiumDb::class.java` passes only
  as a plain argument never followed by a dot (`buildDatabase(CompendiumDb::class.java, …)`,
  `viewModelClass = HomeViewModel::class.java`); generic readers take a `KSerializer<T>`, not a
  `KClass`.
- **What a forgotten `SCHEMA_VERSION` bump throws.** With Room's `version` still 1 and a changed
  `RecordRow`/`SearchRow`, the open fails on the identity-hash check: "Room cannot verify the data
  integrity. Looks like you've changed schema but forgot to update the version number…"
  (`androidx/room/RoomOpenHelper.class` and `BaseRoomConnectionManager.class` in
  `room-runtime-release.aar` 2.7.0, checked against `room_master_table WHERE id = 42`).
  "A migration from X to Y was required but not found" is the other branch — a `version` bump
  without a `Migration` — which this design never takes. The JVM gate pins the column set beside
  the version (`StaleDbFilesTest.aColumnChangeInEitherRowRequiresASchemaVersionBump`).

## Verified 31 Aug 2026 — compendium screens (M2 step 6, `feat/m2-compendium-screens`)

Read out of `light-sdk` @ `3df3c24`; all three are load-bearing for the S13 hub → FIND editor →
S13.4 results round trip, and none is inferable from the July snapshot.

- **A cancel delivers no result at all — the `navigateTo` callback simply never fires.**
  `BackStackEntry.deliverResult` opens `val result = screen.result ?: return`
  (`sdk/client/.../LightActivity.kt:52-55`), and the Activity's back dispatcher calls
  `goBack()` directly (`:139-146`) rather than the screen's own `goBack(result)`, so `result` is
  still null. The hardware back button, a drawn BACK button and an explicit `goBack(null)` are
  therefore indistinguishable to the caller: **"the callback never ran" is the only cancel signal**,
  and a screen that must express cancel needs a nullable result type (`SimpleLightScreen<String?>`)
  so the null branch is expressible at both ends.
- **`goBack()` shows the previous screen *before* it delivers the popped screen's result.**
  The order inside `LightActivity.goBack` is `previous.screen.notifyWillShow()` (`:85`, →
  `onScreenShow`), then `currentScreen.value = previous` (`:86`), then `current.deliverResult()`
  (`:87`). So a screen that pushes an editor and re-queries itself from the result always gets a
  second `onScreenShow` *first*: its load must be guarded (`loaded`), or the re-show would re-run
  the query the result is about to replace. S13.4 depends on this
  (`SearchResultsViewModel.load()`; pinned by `theReShowThatPrecedesAReFindCannotUndoIt`).
- **A `SimpleLightScreen` gets no key handling for free, and silently forwards the wheel.**
  `SimpleLightScreen` *implements* `LightKeyHandler` (`LightScreen.kt:11-12`) whose three methods
  default to `false` (`sdk/ui/.../LightKeyHandler.kt:6-14`); only `LightScreen` overrides them, to
  delegate to its view model (`LightScreen.kt:95-103`). A screen with no view model that overrides
  nothing therefore returns false for every key, and `LightActivity` hands anything in
  `LightDeviceKeys.mapping` to `forwardKeyEventToServer(…, componentToRelaunch = …)`
  (`:157-166`, `:176-183`, `:189-197`, `:199-216`) — LightOS foregrounds itself and relaunches the
  tool. Any full-screen editor must consume 317/318/319 itself (`TextEditorScreen`). Consuming at
  this level cannot starve the composed keyboard: `LightActivity` overrides `onKeyDown`, not
  `dispatchKeyEvent`, so the view hierarchy has already had first refusal.

## Still unknown (ask Light or test)

1. Wheel turn events on retail LightOS (M0).
2. Whether `.db`/`.sqlite` assets or a larger per-file cap will be allowed.
3. Any APK/download size policy at signing or dashboard install.
4. Tool Library submission mechanics and the promised written design guidelines (site says "early Fall").
5. Context-free vibration for tools; file import ("private inbox") API timing.

## Hardware results (fill in during M0)

| Control | Key code observed | Reaches tool? | Date / LightOS |
|---|---|---|---|
| Wheel toward the top of the phone | `KEYCODE_WHEEL_CCW` (317), one DOWN/UP pair per detent | yes | 2026-08-28 / 572-release-lp3 |
| Wheel toward the bottom of the phone | `KEYCODE_WHEEL_CW` (318), one DOWN/UP pair per detent | yes | 2026-08-28 / 572-release-lp3 |
| Wheel press | `KEYCODE_WHEEL_CLICK` (319) DOWN/UP | yes | 2026-08-28 / 572-release-lp3 |
| Volume up / down | `KEYCODE_VOLUME_UP` (24) / `KEYCODE_VOLUME_DOWN` (25) DOWN/UP | yes (tool stays foreground when it consumes them) | 2026-08-28 / 572-release-lp3 |
| Camera half / full | half: `KEYCODE_FOCUS` (80) DOWN/UP; full: FOCUS DOWN, `KEYCODE_CAMERA` (27) DOWN auto-repeating (`repeat=0…5` while held), CAMERA UP, FOCUS UP | yes | 2026-08-28 / 572-release-lp3 |

Method: `examples/ui-demo` (commits `serverPackage = "com.lightos"`) installed with
`./gradlew :examples:ui-demo:installDebug`, its **Key Events** screen consumes every key and lists
`KeyEvent.keyCodeToString`; screenshots over adb are the record. The power (screen on/off) button
hands the screen to LightOS — that is not a key event and is expected. Unconsumed keys were not
tested (the SDK forwards them to LightOS, which relaunches the tool).

## Measured on hardware (TLP301, LightOS 572-release-lp3)

- **First-launch compendium import, 29 Aug 2026** (`feat/m2-compendium-db`, the shipped importer):
  22 JSON chunks → strict typed decode of raw slices → one `withTransaction` writing 1 992
  `records` rows and the standalone `search_index` FTS4 table. Three true first-launch runs
  (phone awake, `pm clear` between): **2 543 / 2 281 / 2 268 ms** total — decode 1 482 / 1 471 /
  1 468 ms, insert 571 / 562 / 558 ms. A relaunch takes the stamp-and-count path
  (`compendium ready rows=1992`, no import line; not timed). On device `compendium-v1.db` is
  6 279 168 bytes plus a WAL of the same size until checkpoint (the plan estimated ≈ 4 MB). The
  first run of the session, while the phone was dozing (screen off), logged **11 460 ms** on a
  throttled CPU and still ended Ready — slow, not broken; no timeout; excluded from the three.
  Supersedes the 28 Aug `spike/import-timing` figures (`JsonElement` decode: 3 254 / 3 183 /
  3 125 ms). Under the 4 s bar in ADR-0002; the `.bin` prebuilt-SQLite fallback stays unscheduled.

Method: the debug APK of the step-5 tree (commit `71617b3`, whose message records the rounded
figures) installed over adb; `adb shell pm clear dev.tyler.grimoire`, launch, wait for Home. The
numbers are `CompendiumStore`'s one `Log.i("Grimoire", …)` line — `compendium import rows=1992
decode=<ms>ms insert=<ms>ms total=<ms>ms` from `ImportResult.Imported` (`System.nanoTime` buckets
around decode and insert inside `AssetImporter.ensure()`), `compendium ready rows=1992` from
`Skipped` — read with `adb logcat -s Grimoire`; the file size from
`adb shell run-as dev.tyler.grimoire ls -l databases/`. Reproduce with `run-light-tool`; the phone
must be awake with the tool in the foreground, or the dozing figure is what you get.
