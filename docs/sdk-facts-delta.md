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

## Measured on hardware (28 Aug 2026, TLP301, LightOS 572-release-lp3)

- First-launch compendium import (`spike/import-timing`, commit b811c63): 22 JSON chunks →
  `JsonElement` decode → one Room `insertAll` of 1 992 `(kind, key, name, json)` rows with an FTS4
  content table. Three true-first-launch runs (`pm clear` between): **3 254 / 3 183 / 3 125 ms**
  total — decode 2 508 / 2 486 / 2 429 ms, insert+FTS 741 / 691 / 691 ms, `MATCH 'fire*'` 4 ms
  (169 hits). Under the 4 s bar in ADR-0002; the `.bin` prebuilt-SQLite fallback is not scheduled.
