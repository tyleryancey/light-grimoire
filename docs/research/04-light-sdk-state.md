# 04 — Light SDK state, verified against source (29 Aug 2026)

House rule applied throughout: *the README and spec docs drift; the SDK source does not lie.* Every
claim below cites a file path (relative to the `light-sdk` repo root unless stated) and, where it
matters, a line number. Where a document conflicts with source, the correction is written down.

Local checkouts used for this pass (all shallow clones made 29 Aug 2026 ~01:00 UTC):

| Repo | Path | Commit |
|---|---|---|
| `lightphone/light-sdk` | `/home/claude/dnd-companion/light-sdk-src` | `3df3c24a21247e70ad59e1bc0393ac6d63840bc2` (2026-08-25) |
| `lightphone/light-keyboard` | `/home/claude/dnd-companion/light-keyboard-src` | `1755571b…` = tag `v0.0.18` (2026-08-05) |
| `garado/awesome-light` | `/home/claude/dnd-companion/awesome-light-src` | `a070a50c…` (2026-08-27) |
| `garado/dictionary`, `cmg-ops/LP3-Bible` | `dict-src`, `bible-src` | precedent checks only |

---

## (A) Verification target

| Item | Value | Evidence |
|---|---|---|
| Commit verified | `3df3c24a21247e70ad59e1bc0393ac6d63840bc2` — "Merge pull request #172 from lightphone/feat/audio-15min-idle-stop" | `git log -1` |
| Commit date | 2026-08-25 10:59 -0300 | `git log -1` |
| SDK version | `sdkVersion=0.1.1` (group `com.thelightphone`) | `gradle.properties:5-6` |
| Nearest tag | `v0.1.1` = `bdffe1c` (2026-08-20). HEAD is 2 commits past it: `ea71e98` "increase idle stop audio timeout to 15 min" + the merge. | `git ls-remote --tags`; `git log` |
| Other tags | `v0.0.3, v0.0.4, v0.0.8, v0.0.10, v0.0.11, v0.0.13, v0.1.1` (no GitHub Releases published — releases page says "There aren't any releases here"; 222 stars / 73 forks) | `git ls-remote`; github.com/lightphone/light-sdk/releases |
| Plugin version | The plugin no longer carries its own version string; it inherits `sdkVersion` from `../gradle.properties` (`version = rootProps.getProperty("sdkVersion")`). So "plugin 0.1.1". | `plugin/build.gradle.kts:8-13` |
| Toolchain | JDK 17 (`jvmToolchain(17)`), Kotlin 2.3.20, AGP 8.12.3, KSP 2.3.6, Compose BOM 2026.03.01, Room 2.7.0, Work 2.10.0, media3 1.10.1, Ktor 3.4.2 | `plugin/build.gradle.kts:4,30`; `gradle/libs.versions.toml:1-16` |
| Android targets | `compileSdk=36`, **`minSdk=34`**, `targetSdk=36`, `jvmTarget=17` | `build.gradle.kts:13-16` |
| Post-July commits reviewed | 70+ commits, 2026-07-10 → 2026-08-25 (`git fetch --shallow-since=2026-06-15`) | `git log --format='%h %cd %s'` |

The July snapshot (`plugin-0.0.12`, commit `560def6`, 2026-07-10) was diffed directly against HEAD:
`git diff --stat 560def6 HEAD` = 226 files, +8451/−2695.

---

## (B) Deltas vs the July snapshot

Legend: **WRONG** = the July fact is now false; **NEW** = did not exist in July; **CORRECTION** = July wording was inaccurate even then; ✓ = unchanged, re-verified.

| # | Fact | July snapshot | Now (29 Aug 2026) | Evidence |
|---|---|---|---|---|
| 1 | SDK/plugin version | `plugin-0.0.12` | `sdkVersion=0.1.1` (tag `v0.1.1`, 2026-08-20) | `gradle.properties:6` |
| 2 | `minSdk` | 33 | **WRONG → 34.** Emulator/AVD guidance is API 34 too. | `build.gradle.kts:14` (diff: `-ext["minSdk"] = 33` / `+ext["minSdk"] = 34`); `README.md` Quickstart ("Android API 34"); `docs/system_app/README.md` §1 |
| 3 | GitHub Packages credentials needed for `com.thelightphone.lp3keyboard` (`gpr.user`/`gpr.key`, `GH_PACKAGES_USER`/`GH_PACKAGES_TOKEN`) | Required; "README's property names are wrong — source wins" | **WRONG → not required.** Keyboard now resolves from **JitPack** as `com.github.lightphone:light-keyboard:v0.0.18`; `settings.gradle.kts` no longer reads any credentials; README token section deleted. (`gpr.user`/`gpr.key` survive only in root `build.gradle.kts:24-35` for *publishing* the SDK — irrelevant to tool authors.) | `settings.gradle.kts:9-17`; `gradle/libs.versions.toml:60`; commit `6a7cbb6` (2026-08-04) "pull lp3 keyboard from jitpack instead of github packages"; README diff |
| 4 | `kotlinx-datetime` | "**not** allowed — use `java.time`" | **WRONG → allowed** (`org.jetbrains.kotlinx:kotlinx-datetime`, v0.8.0 in catalog; weather example uses it). `java.time` still fine. | `plugin/…/LightSdkPlugin.kt:30`; `gradle/libs.versions.toml:36`; `examples/weather/build.gradle.kts` (`implementation(libs.kotlinx.datetime)`); commit `c4a502c` (2026-07-28) |
| 5 | `ALLOWED_DEPENDENCIES` size | 19 prefixes | **27 prefixes.** 8 NEW: `org.jetbrains.kotlinx:kotlinx-datetime`, `com.github.lightphone:light-keyboard`, `androidx.media3`, `org.bouncycastle:bcprov-jdk18on`, `com.google.zxing:core`, `org.sol4k:sol4k`, `org.sol4k:tweetnacl`, `org.sol4k:utilities`. Full list in §B.1 below. | `LightSdkPlugin.kt:17-45` |
| 6 | `BLOCKED_IMPORTS` | 16 prefixes | ✓ identical 16 | `LightSdkPlugin.kt:77-94` |
| 7 | `BLOCKED_CODE_PATTERNS` | 29 regexes | ✓ identical (LocalContext/LocalView/LocalActivity/LocalLifecycleOwner, `as? …Activity`, `as? …Context/…`, startActivity/startService/bindService/registerReceiver/getSystemService, contentResolver, getBaseContext/attachBaseContext, 7× `create*Context(`, `.javaClass`, `.java.<w>`, `Class.forName(`, get(Declared)Method/Field, `MethodHandles`) | `LightSdkPlugin.kt:96-126` |
| 8 | `ALLOWED_PLUGINS` | 8 ids | ✓ identical | `LightSdkPlugin.kt:47-56` |
| 9 | `ALLOWED_KSP_PROCESSORS` | `androidx.room:room-compiler` only | ✓ identical; self-added plugin jar is the only allowed file dep on `ksp*` | `LightSdkPlugin.kt:73-75, 223-231, 430-441` |
| 10 | Build-script bans | universal + consumer sets | ✓ identical (`buildscript{`, `resolutionStrategy`, `dependencySubstitution`, `apply(plugin/from/<>/{})`, `pluginManager.apply`, `srcDir(s)`; consumer: `applicationId=`, `versionCode=`, `versionName=`, `namespace=`) | `LightSdkPlugin.kt:129-147` |
| 11 | Scan mechanics (configure-time, walks all of `tool/src` incl. tests, per-line split on `;`, comment-*line* exemption only, `.java` rejected, deps validated declared+resolved) | as described | ✓ identical | `LightSdkPlugin.kt:184-219, 240, 366-383, 422-517` |
| 12 | `lighttool.toml` required fields | "All **six** fields are required" incl. `permissions` | **WRONG → 5 required** (`id`, `label`, `versionCode`, `versionName`, `serverPackage`); **`permissions` is optional** (defaults to `[]`); **NEW optional `capabilities`** (allow-list = `["detached-audio"]` only); **NEW optional `orientation`** (allow-list = `"portrait"` only). | `LightToolMetadata.kt:56-65` (`tomlStringList("permissions")` → `values ?: emptyList()` at `:120-121`), `:142-152`, `:111-118`, `:174`, `:190-192`; test `LightToolMetadataTest.kt:44-56` parses a toml with no `permissions` key |
| 13 | Field regexes | id / label / versionName / versionCode range | ✓ identical: `TOOL_ID_PATTERN ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$`; `TOOL_LABEL_PATTERN ^[^\x00-\x1f<>]{1,50}$`; `VERSION_NAME_PATTERN ^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$`; `versionCode` 1..2_100_000_000; file ≤ 32 KiB; `serverPackage` same regex as id. | `LightToolMetadata.kt:29,169-173` |
| 14 | `ALLOWED_PERMISSIONS` | 11 | ✓ identical 11 (INTERNET, ACCESS_NETWORK_STATE, WAKE_LOCK, VIBRATE, POST_NOTIFICATIONS, CAMERA, RECORD_AUDIO, READ_MEDIA_AUDIO, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, NFC). No `READ_MEDIA_IMAGES`, no `SCHEDULE_EXACT_ALARM`, no BLUETOOTH. | `LightToolMetadata.kt:176-188` |
| 15 | Capability-implied permissions | — | **NEW:** `detached-audio` ⇒ `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK`; hand-listing either under `permissions` fails with a message naming the capability. | `LightToolMetadata.kt:120-140, 200-205`; test `:120-140` |
| 16 | `PERMISSION_IMPLIED_FEATURES` | CAMERA/RECORD_AUDIO/2×location/NFC → `<uses-feature required=false>` | ✓ identical | `LightToolMetadata.kt:223-229` |
| 17 | Manifest generation | inline in `applyToolMetadata` | Moved to **`ManifestGenerator.render(metadata)`**; additionally emits `<meta-data com.thelightphone.sdk.CAPABILITY_DETACHED_AUDIO>`, the `LightAudioService` (`foregroundServiceType="mediaPlayback"`) when `detached-audio` is declared, and `android:screenOrientation` when `orientation` is set. Application theme `@style/LightSdk.Theme.Splash`; receiver `<meta-data SDK_VERSION="${sdkVersion}">` — the tool build script must set `manifestPlaceholders["sdkVersion"]` (scaffold does). Client library manifest merges in `LightFileProvider` (exported, gated to `SYSTEM_UID`), `LightPushService`, and **`<uses-feature android.hardware.usb.host required=false>`** ("Lets tools talk to USB security keys"). | `plugin/…/ManifestGenerator.kt:15-103`; `LightSdkPlugin.kt:255-304`; `tool/build.gradle.kts:28`; `sdk/client/src/main/AndroidManifest.xml:5-16`; `sdk/client/…/LightFileProvider.kt:19-23` |
| 18 | `-DlightSdk.unsigned=true` | strips signing for the server builder | ✓ identical; **NEW `builder/`** directory documents the actual server pipeline (see §D.5) | `LightSdkPlugin.kt:282-303`; `builder/README.md` |
| 19 | Exactly one `@InitialScreen` — "zero or multiple fails the KSP build" | as stated | **CORRECTION:** KSP fails only on **>1**. Zero compiles (`initialScreenFactory = null`) and crashes at launch: `IllegalStateException("No class annotated with @InitialScreen found")`. The client README's "excluding it … will fail the build" is doc drift. | `plugin/…/LightSdkProcessor.kt:42-48, 120-124`; `sdk/client/…/LightActivity.kt:105-106`; `sdk/client/README.md` §Screens |
| 20 | "Exactly one `@EntryPoint object`" | required | **CORRECTION: optional (0 or 1).** >1 fails KSP; must be an `object`. New members: `enablePushNotifications` (default false) and `enableRecentsScreenshots` (default false — suppresses LightOS's stale-screenshot flash on re-foreground). | `LightSdkProcessor.kt:51-64, 125-129`; `sdk/client/…/EntryPoint.kt:12-28`; README "Tool entry point (optional)" |
| 21 | Hardware keys / scroll wheel | not covered | **NEW** — full API; see §F | `sdk/ui/…/LightKeyHandler.kt`; `LightScreen.kt:11-12, 95-103`; `LightViewModel.kt:6`; `LightActivity.kt:149-216` |
| 22 | `SealedLightContext` capabilities | "DataStore / Room" | Members: `dataStore: DataStore<Preferences>` (name `DEFAULT_DATASTORE`), `filesDir: File`, `fileShare: LightFileShare`, **NEW `connectivity: LightConnectivity`**, **NEW `readAsset(path): ByteArray`**. Extension `SealedLightContext.buildDatabase(dbClass, dbName)` (Room). `androidContext` is `internal` — tools never see a `Context`. | `LightActivity.kt:229-244`; `LightDb.kt:6-8` |
| 23 | `LightTextInputEditor` signature | `(title, state, onSubmit, onBack, keyboardOptionsFlow, …)` | Same core, **plus** `submitLabel="SUBMIT"`, `submitIcon: LightIconConfiguration?`, `showBackButton=true`, `singleLine=false`, `initialCaps=false`, `editorKey`; second overload takes `viewModel: Lp3KeyboardViewModel<*>`. Submit control moved into the keyboard's bottom-bar alley (keyboard v0.0.18). Text area is **not scrollable** (`BasicText` in a `Column`, `overflow=Clip`). | `sdk/ui/…/LightTextInputEditor.kt:47-61, 103-115, 135-195`; commit `81562cf` |
| 24 | `LightTextField` read-only `(label, value, placeholder, onClick, modifier)` | as stated | ✓ identical | `LightTextField.kt:23-30` |
| 25 | `LightFullscreenModal(message, onClose, modifier)` — no content slot | as stated | ✓ identical. **NEW** transient overlay system: `LightModal` + `LightModalManager.show(modal, 2s)` (drawn by `LightActivity` above the screen). | `LightFullscreenModal.kt:15-20`; `LightModalManager.kt:25-95`; `LightActivity.kt:133-135` |
| 26 | `LightGrid` constants object (27×31) + extension helpers | as stated | ✓ identical | `LightGrid.kt:13-42` |
| 27 | Theme = `background/content/contentSecondary`, monochrome not enforced | as stated | ✓ identical (`Dark`: #000/#FFF/#BBB; `Light`: #FFF/#000/#666) | `LightTheme.kt:31-65` |
| 28 | Icons | — | 106 `LightIcons` objects; ~100 `*_black` drawables deleted (white-only, tinted); `TOGGLE_STATE_ON/OFF` art swapped-fix (commit `3fa2281`). | `LightIcons.kt`; diff stat |
| 29 | Lifecycle hooks | `onScreenShow/Hide`, `onAppPause`, `onBackPressed` | ✓ plus screen-level `willShow/willHide/onAppPause/onScreenDestroy`. **CORRECTION:** `onResume` re-fires `willShow`/`onScreenShow` (not just navigation). | `LightScreen.kt:19-22, 74-87`; `LightActivity.kt:218-226` |
| 30 | Back handling | "override `onBackPressed` in your `LightViewModel`" (README) | **CORRECTION:** `viewModel.onBackPressed()` is consulted only when the *tool* calls `screen.goBack()`. The Activity-level path (`OnBackPressedDispatcher` → `LightActivity.goBack()`) pops without asking. And the README's "renders a back bar at the bottom of the screen" is false — `LightActivity` renders no bar; every example draws its own BACK in `LightTopBar`. | `LightScreen.kt:89-93`; `LightActivity.kt:73-88, 139-146, 113-137` |
| 31 | `LightWork` | 15-min floor, UPDATE policy, `onToolCreate` has no context | ✓ identical | `LightWork.kt:161-180`; `EntryPoint.kt:13` |
| 32 | `clean` must be a separate invocation | plan-derived | Structurally still true: the manifest is written at **configure** time into `build/generated/light-sdk/`; a `clean` task in the same invocation deletes it before AGP's manifest tasks run. | `LightSdkPlugin.kt:263-280` |
| 33 | Release build | `assembleDebug` is the local gate | **NEW emphasis:** the server builder runs **`:tool:assembleRelease --offline -DlightSdk.unsigned=true`**, and the scaffold's release type has **R8 `isMinifyEnabled=true` + `isShrinkResources=true`**. Run `:tool:assembleRelease` locally before submitting. | `tool/build.gradle.kts:35-40`; `builder/README.md` (architecture block) |
| 34 | Tool Library status | "submissions expected end of August" | **Not open.** No submission template in the repo; README still says "As of July 1, 2026, there's no 'easy' way to share your tool"; dev site says "By early Fall"; LightOS v572 (Aug 20) added only "view third party tools and their versions in Settings". See §G. | `README.md` §Sharing Your Tool; `.github/` (only `pr-check.yml`); support changelog |
| 35 | `LightAudio`, NFC, connectivity, haptics, `OpenDialer`, `GetUserPreferences` | — | **NEW**, see §D | `sdk/client/src/main/kotlin/com/thelightphone/sdk/{audio,nfc}/*`, `LightConnectivity.kt`, `LightHapticsManager.kt`, `sdk/shared/…/LightServiceMethod.kt:81-146` |

### B.1 `ALLOWED_DEPENDENCIES` — full list (`LightSdkPlugin.kt:17-45`, prefix match on `group:name`)

```
org.jetbrains.kotlin:kotlin-stdlib
org.jetbrains.kotlin:kotlin-test
androidx.compose
androidx.activity:activity-compose
androidx.annotation
org.jetbrains.kotlinx:kotlinx-coroutines
androidx.lifecycle
androidx.datastore
com.squareup.okhttp3:okhttp
io.ktor
org.jetbrains.kotlinx:kotlinx-serialization
org.jetbrains.kotlinx:kotlinx-io
org.jetbrains.kotlinx:kotlinx-datetime          (NEW 2026-07-28)
org.unifiedpush.android:connector
androidx.core:core-splashscreen
com.thelightphone.lp3keyboard
com.github.lightphone:light-keyboard            (NEW 2026-08-04, JitPack)
androidx.room
androidx.work
androidx.startup
androidx.media3                                 (NEW 2026-07-24/08-05)
io.github.david-allison:anki-android-backend
org.bouncycastle:bcprov-jdk18on                 (NEW 2026-08-07)
com.google.zxing:core                           (NEW 2026-08-13)
org.sol4k:sol4k                                 (NEW 2026-08-13)
org.sol4k:tweetnacl                             (NEW 2026-08-13)
org.sol4k:utilities                             (NEW 2026-08-13)
```

### B.2 `lighttool.toml` as it stands (`docs/tool_metadata/README.md` + `LightToolMetadata.kt`)

```toml
[tool]
id           = "com.example.mytool"   # required; ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$ ; permanent once published; namespace derives from it
label        = "My Tool"              # required; 1–50 printable chars, no < > or control chars
versionCode  = 1                      # required; 1..2_100_000_000; build server rejects non-increasing resubmits (docs claim)
versionName  = "1.0.0"                # required; strict major.minor.patch, no suffixes
permissions  = []                     # OPTIONAL (default []); each ∈ ALLOWED_PERMISSIONS; no duplicates
capabilities = []                     # OPTIONAL; only "detached-audio"
orientation  = "portrait"             # OPTIONAL; only "portrait"
serverPackage = "com.lightos"         # required; "com.thelightphone.sdk.emulator" on the AVD
```

Real-world drift example: `cmg-ops/LP3-Bible` still ships `versionName = "2.0"` — it would fail today's regex
(`bible-src/tool/lighttool.toml`).

---

## (C) `sdk:ui` inventory (complete, from `sdk/ui/src/main/kotlin/com/thelightphone/sdk/ui/`)

### C.1 Composables and modifiers

| Name | Parameters | Purpose / notes | File |
|---|---|---|---|
| `LightText` | `(text, variant: LightTextVariant, modifier, align: TextAlign?=null, lighten=false, underline=false, monospace=false, maxLines=Int.MAX, overflow=Clip, color: Color?=null)` | The text primitive. `lighten` → `contentSecondary`. Styles scaled by screen height (see C.4). | `LightText.kt:75-108` |
| `LightTheme` | `(colors=LightThemeColors.Dark, typography=rememberLightTypography(), surfaceScheme=colors.inferredSurfaceScheme(), content)` | Provides Light tokens + an M3 `MaterialTheme` mapped from them. Wrap every screen's `Content()`. | `LightTheme.kt:202-219` |
| `rememberLightTypography` | `()` | Builds the scale with Akkurat (system font on LP3, else bundled `akkuratll_*` font resources, else default). | `LightTheme.kt:153-157`; `LightFont.kt` |
| `LightTopBar` | `(leftButton: LightTopBarButton?=null, center: LightTopBarCenter?=null, rightButton=null, modifier)` | 3-grid-unit bar; center max width 18 units; text variant `Fine`. `LightTopBarCenter.Text(text,onClick)` / `.TwoLineDetail(line1,line2,onClick)`. | `LightTopBar.kt:24-98` |
| `LightBottomBar` | `(items: List<LightBottomBarItem?>, modifier, topPadding=1 unit)` | 4-unit action bar; **≤5 items; ≤3 if any is `Text`**; `null` = spacer slot. Text variant `Button`. | `LightBottomBar.kt:30-115` |
| `LightBarButton` | `.Text(text, contentDescription?, onClick?)`, `.Icon(painter, onClick?, contentDescription?, sizeUnits=2f)`, `.LightIcon(icon: LightIconConfiguration, onClick?, contentDescription=icon.name, sizeUnits=2f)` | Sealed button model for both bars (typealiases `LightTopBarButton`, `LightBottomBarItem`). `onClick=null` renders static. | `LightBarButton.kt:14-53` |
| `LightIcon` | `(icon: LightIconConfiguration, modifier, width=2f, height=2f, size: Float?=null, contentDescription=icon.name)` | Tinted to `colors.content`; sizes in grid units. | `LightIcon.kt:22-46` |
| `LightIcons` | 106 objects (see C.3) | Registry of `LightIconConfiguration(name, drawableResource)`. | `LightIcons.kt` |
| `Modifier.lightClickable` | `(enabled=true, hapticsEnabled=true, onClickLabel=null, role=null, onClick)` | Click with **no ripple**; fires a 45 ms haptic on finger-down when the user's LightOS haptics pref is on (`LocalHapticsEnabled`). This is the *only* haptic a tool can trigger. | `LightClickable.kt:17-48` |
| `LightScrollView` | `(modifier, scrollBarPosition=Outside, scrollState=rememberScrollState(), content: ColumnScope.() -> Unit)` | Non-lazy `Column` + Light scrollbar (2-unit gutter, draggable thumb, tap-to-jump). | `LightScrollView.kt:96-144` |
| `LightLazyScrollView` | `(modifier, scrollBarPosition=Outside, listState=rememberLazyListState(), uniformItemHeightGridUnits: Float, content: LazyListScope.() -> Unit)` | `LazyColumn` + scrollbar. **Requires uniform item height** (scrollbar math assumes it). | `LightScrollView.kt:146-232` |
| `LightScrollBarPosition` / helpers | `Outside`, `Inside`; `scrollBarGutterUnits(pos)`, `scrollViewContentWidthUnits(total,pos)` | Layout math for content beside the bar. | `LightScrollView.kt:51-94` |
| `LightTextField` | `(label, value, placeholder, onClick, modifier)` | **Read-only** display (label `Detail`, value `Copy`, 80 %-width underline); tap opens an editor screen. No text-change callback. | `LightTextField.kt:23-61` |
| `LightTextInputEditor` | `(title, state: TextFieldState, onSubmit: (CharSequence)->Unit, onBack, keyboardOptionsFlow: StateFlow<KeyboardOptions>, modifier, submitLabel="SUBMIT", submitIcon=null, showBackButton=true, singleLine=false, initialCaps=false, editorKey)` (+ overload with `viewModel: Lp3KeyboardViewModel<*>`) | **Full-screen** text entry: top bar, `Heading`-style input with tap/drag caret placement, embedded LP3 keyboard, submit in the bottom-bar alley. Multi-line by default (Return inserts `\n`; `singleLine=true` makes Return submit). Input area **does not scroll** — long notes clip. Drive with `rememberTextFieldState()` + `rememberKeyboardOptions()` (client). | `LightTextInputEditor.kt:47-221` |
| `defaultKeyboardOptions` | `()` | Fallback `KeyboardOptions` (emojis, return, voice, animation on, swipe off). | `LightTextInputEditor.kt:271-277` |
| `LightEmbeddedLp3Keyboard` | `(viewModel: Lp3KeyboardViewModel<*>, additionalBottomHeight=0.dp, bottomBar=null, onOverlayDismissed=null, overlay=null)` | Themed wrapper around `Lp3KeyboardWrapper` for custom keyboard screens. | `keyboard/LightEmbeddedLp3Keyboard.kt:23-61` |
| `LightFullscreenModal` | `(message, onClose, modifier)` | Centered message + single CLOSE bottom-bar button. No content slot. | `LightFullscreenModal.kt:15-51` |
| `LightModal` / `LightModalManager` | interface `{ Content(); onExpired; dismiss(); awaitDismiss() }`; `show(modal, duration=2s)`, `dismiss()`, `activeModal: StateFlow` | Transient overlay drawn by `LightActivity` above the current screen (LightOS volume-modal style). One at a time; auto-dismiss. | `LightModalManager.kt:25-95` |
| `LightProgressBar` | `(colors: LightColors, progress: Float)` | 0.1-unit rail + 0.5-unit fill. | `LightProgressBar.kt:26-42` |
| `LightTouchableProgressBar` | `(colors, progress, onValueChange: (Float)->Unit, modifier)` | 3-unit-tall draggable slider on top of `LightProgressBar`. The only slider-like control. | `LightProgressBar.kt:44-80` |
| `LightQrCodeScanner` (ui) | `(onScanned, onBack, modifier, title="Scan QR Code", checkCameraPermission, launchCameraPermissionRequest)` | CameraX + ML Kit scanner screen. Client wraps it as `LightQrCodeScanner(onScanned, onBack, modifier, title)` with permission plumbing. | `LightQrCodeScanner.kt:58-66`; `sdk/client/…/LightClientUiUtils.kt:37-58` |
| `LightNfcTapReader` (ui) | `(state: LightNfcTapState, onBack, modifier, title="Tap", prompt)` | Prompt screen; client wrapper `LightNfcTapReader(onTap: (LightNfcTap)->Unit, onBack, modifier, title, prompt)` runs the reader while showing. | `LightNfcTapReader.kt:29-44`; `LightClientUiUtils.kt:66-116` |
| `LightKeyHandler` | `onKeyDown(keyCode, event)`, `onKeyUp(…)`, `onKeyMultiple(keyCode, repeatCount, event)` → `Boolean` (default `false`) | Hardware-key interface implemented by `SimpleLightScreen` and `LightViewModel`. | `LightKeyHandler.kt:5-15` |
| `LightHapticFeedback` | `click(context)`, `vibrateForDuration(context, duration)` | Needs an Android `Context` → **unreachable from tool code** (no Context source; `android.content.Context` import is blocked). Used internally by `lightClickable` and the keyboard. | `LightHapticFeedback.kt:9-18` |
| `LocalHapticsEnabled` | `compositionLocalOf { false }` | Provided by `LightActivity` from `GetUserPreferences.hapticsEnabled`. | `LightClickable.kt:17`; `LightActivity.kt:124-127` |
| Client-side composables/helpers | `rememberKeyboardOptions()`, `rememberHapticsEnabled()`, `rememberPermissionRequestLauncher(permission)`, `rememberLightNfc()`, `suspend checkPermission(permission)`, `suspend refreshKeyboardOptions()`, `suspend refreshHapticsEnabled()` | Server-backed state for the editor, haptics, and runtime permissions. | `sdk/client/…/LightKeyboardManager.kt`, `LightHapticsManager.kt`, `LightServiceConnection.kt:185-225`, `LightClientUiUtils.kt:60-64` |

**Not present in `sdk:ui`** (verified by listing every file): no toggle/switch, checkbox, radio, segmented control, tabs, divider, number picker/stepper, dialog-with-content-slot, list-item/row component, inline text field, search box, chip, or wheel-aware component. Examples build lists from `LightText` rows + `lightClickable`, and toggles from `LightIcons.TOGGLE_STATE_ON/OFF` / `SELECT_ON/OFF` glyphs.

### C.2 Relevance to the D&D companion

| Need | What the SDK offers | Notes |
|---|---|---|
| Tap-to-increment counters (HP, hit dice, death saves) | `LightText` + `lightClickable` (the `ui-demo` counter does exactly this: `UiDemoSecondScreen.kt:74-90`), optional `LightIcons.UP/DOWN/ADD`; `LightTouchableProgressBar` for a draggable HP bar; hardware wheel via `LightKeyHandler` (§F) | No stepper; build one from two glyph buttons + a `Subtitle`/`Title` number. Haptic on tap is automatic. |
| Toggles (spell slots, conditions, prepared spells) | `LightIcons.TOGGLE_STATE_ON/OFF`, `SELECT_ON/OFF`, `CIRCLE`, `STAR/STAR_OUTLINE` inside `lightClickable` | Glyph-only; no switch component. |
| Long text (spell descriptions) | `LightText(variant=Paragraph/Copy)` inside `LightScrollView` | `Paragraph` 24.5 sp × 1.25 lh (unscaled) is the densest readable variant; `Detail` 20 sp for stat blocks. |
| Searchable list | `LightTextField` (shows query) → `navigateTo(editorScreen)` (`LightTextInputEditor`, `singleLine=true`) → result callback filters a `LightLazyScrollView` (uniform rows) or `LightScrollView` | Canonical pattern in `examples/weather` + `ui-demo` TextInput screens. No inline search box exists. |
| Multi-line notes | `LightTextInputEditor` default (`singleLine=false`) | Editor area clips beyond the visible region — cap note length or split notes; `initialCaps=true` for fresh notes. |
| Confirmations | A `SimpleLightScreen<Boolean>` with CONFIRM in the bottom bar (`AuthenticatorConfirmRemoveScreen.kt`) or `LightFullscreenModal` for messages | No dialog component. |

### C.3 `LightIcons` (106) — `sdk/ui/…/LightIcons.kt`

ACCEPT ADD AIRPLANE ALARM ARROW_DOWN AUDIO_MESSAGE BACK BATTERY_ERROR BATTERY_EMPTY BATTERY_ONE_QUARTER BATTERY_HALF BATTERY_THREE_QUARTERS BATTERY_FULL BATTERY_ALMOST_FULL BATTERY_CHARGING BLUETOOTH CALL CALL_MISSED CAMERA_BRIGHTNESS CAMERA CAMERA_FLASH_ON CAMERA_FLASH_OFF CAMERA_FLASH_AUTO CAMERA_LANDSCAPE CAMERA_SETTINGS CAMERA_RECORDING CAMERA_FOCUS_LOCKING CAMERA_FOCUS_LOCKED CE_MARK CIRCLE CLOSE COMPOSE_MESSAGE PENCIL DELETE DENY DIALPAD DIRECTIONS_ARRIVAL DIRECTIONS_LEFT DIRECTIONS_RIGHT DIRECTIONS_SLIGHT_LEFT DIRECTIONS_SLIGHT_RIGHT DIRECTIONS_MIDDLE_FORK DIRECTIONS_STRAIGHT DIRECTIONS_BUS DIRECTIONS_SUBWAY DIRECTIONS_TRAIN DIRECTIONS_PEDESTRIAN DIRECTIONS_ROUNDABOUT DIRECTIONS_U_TURN_RIGHT DIRECTIONS_U_TURN_LEFT DIRECTIONS_FERRY DOWN EMERGENCY FCC_MARK LIGHT_LOGO FAST_FORWARD LIST LOOP MEDIA MICROPHONE MUTE PAUSE PLAY REWIND SAVE_TO_ALBUM SEARCH SELECT_OFF SELECT_ON SEND SETTINGS SHUFFLE SIGNAL_1 SIGNAL_2 SIGNAL_3 SIGNAL_4 SIGNAL_NONE SPEAKER STAR STAR_OUTLINE TETHERING TOGGLE_STATE_OFF TOGGLE_STATE_ON UP VOICE_MAIL VOICE_MEMO WEEE_MARK WIFI WIFI_NO_INTERNET LARGE_LIST DOWNLOADED_ARROW DOWNLOAD_ARROW SKIP_BACKWARD_FIFTEEN SKIP_FORWARD_FIFTEEN REFRESH MAP CROSSHAIR ARROW_RIGHT STOP CONTACTS REVERSE_ORDER ELLIPSES SPACER TRASH SPEAKER_ON SPEAKER_MUTED ROTATE

No dice, sword, shield, heart, or d20 glyph — any D&D iconography must be custom `LightBarButton.Icon(painter)` art or text.

### C.4 Typography scale (`LightTheme.kt:72-149`, "mirror the LP3 table in `LightOS/src/style/index.ts` (unscaled)")

| Variant | Size (sp, unscaled) | Weight | Letter-spacing | Line-height |
|---|---|---|---|---|
| Title | 115 | Light | — | ×1.10 |
| Subtitle | 52 | Normal | — | ×1.20 |
| Heading | 38 | Normal | — | ×1.35 |
| Subheading | 30 | Normal | 0.03 em | ×1.25 |
| Copy | 30 | Normal | — | ×1.50 |
| Button | 30 | Medium | 0.15 em | ×1.10 |
| Paragraph | 24.5 | Normal | — | ×1.25 |
| ParagraphWide | 25 | Normal | 0.02 em | ×1.30 |
| Detail | 20 | Normal | — | ×1.45 |
| Fine | 25 | Normal | 0.03 em | ×1.15 |
| Superfine | 16 | Normal | — | ×1.20 |
| Micro | 8 | Normal | — | ×1.20 |

Every size/line-height/letter-spacing is multiplied by `screenHeightDp / 600` at draw time
(`LightText.kt:57-73` → `Float.designVerticalPxToSp()`, `LightGrid.kt:30-36`). Font family = Akkurat
(`LightFont.kt`). Bar heights: top 3 units, bottom 4 units; default icon 2 units; `LightGrid.WIDTH=27`,
`HEIGHT=31` (`1f.gridUnitsAsDp()` = screenWidthDp/27).

---

## (D) Client framework & storage facts (`sdk/client`)

### D.1 Screens, view models, navigation (`LightScreen.kt`, `LightViewModel.kt`, `LightActivity.kt`)

- `SimpleLightScreen<R>(sealedActivity)` : `LightKeyHandler` — `abstract @Composable Content()`; hooks `willShow()`, `willHide()`, `onAppPause()`, `onScreenDestroy()`; `protected val lightContext: SealedLightContext`; `navigateTo(factory: (SealedLightActivity) -> SimpleLightScreen<T>, resultCallback: ((T)->Unit)? = null)`; `open goBack(result: R? = null)`.
- `LightScreen<R, VM : LightViewModel<R>>` adds `abstract val viewModelClass: Class<VM>` (a bare `::class.java` is allowed by the scan), `abstract createViewModel()`, lazy `viewModel`, own `ViewModelStore` cleared on pop; forwards `willShow→vm.onScreenShow(screen)`, `willHide→vm.onScreenHide`, `onAppPause→vm.onAppPause`, keys → vm; `goBack` first asks `vm.onBackPressed()` (return `true` to consume).
- `LightViewModel<T>` : `ViewModel(), LightKeyHandler` — `onScreenShow/onScreenHide/onAppPause/onBackPressed(): Boolean=false`.
- `LightActivity` (internal ctor): in-memory back stack; `navigateTo` = hide current → push → `willShow`; `goBack` = hide+destroy popped → clear its VM store → pop → `willShow` previous → deliver result; empty stack ⇒ `finish()`. `onPause → notifyAppPause`; **`onResume → notifyWillShow`** (so `onScreenShow` fires again after LightOS takes the screen for a volume/wheel modal, a call, etc.). Splash held ≥1 s (`LightActivity.kt:91-93`). System bars hidden, `FLAG_LAYOUT_NO_LIMITS`.
- Back: `OnBackPressedDispatcher` → `goBack()` directly (bypasses `vm.onBackPressed()`); no SDK back bar is drawn.
- Registry: KSP-generated `com.thelightphone.sdk.generated.LightSdkRegistry` (initial screen factory, entry point, jobs). Consumer ProGuard keeps `@InitialScreen` classes, `LightEntryPoint` impls, generated registry, `LightIcons` (`sdk/client/consumer-rules.pro`, `sdk/ui/consumer-rules.pro`). Room entities/DAOs rely on Room's own rules; kotlinx-serialization classes rely on the plugin's — verify with a release build.

### D.2 `@InitialScreen` / `@EntryPoint` / `@LightJob` (`plugin/…/LightSdkProcessor.kt`)

- `@InitialScreen`: ≤1 class; constructor `(SealedLightActivity)`; 0 ⇒ runtime crash (see B.19).
- `@EntryPoint`: ≤1, must be an `object : LightEntryPoint`; `suspend onToolCreate(serverData: StateFlow<LightServerData?>)` (called once from `LightSdkApplication.onCreate`, **no `SealedLightContext`**), `suspend onPushNotification(data: ByteArray)`, `enablePushNotifications`, `enableRecentsScreenshots`.
- `@LightJob("key") val x: LightJobHandler` (`suspend (SealedLightContext, Map<String,String>) -> LightJobResult`): must be top-level, non-empty unique key. `LightWork.enqueue/enqueuePeriodic(≥15 min, UPDATE)/cancel/observe/getState/awaitCompletion` (`LightWork.kt:114-227`). A job's `SealedLightContext` is built from `applicationContext` (`:238`).

### D.3 What a tool can actually do with `SealedLightContext` (`LightActivity.kt:229-235`)

| Capability | API | Verdict |
|---|---|---|
| Key/value prefs | `lightContext.dataStore: DataStore<Preferences>` (`DEFAULT_DATASTORE`, shared tool-wide) | ✓ (`examples/weather/WeatherPreferences.kt`) |
| SQLite | `lightContext.buildDatabase(MyDb::class.java, "name.db")` → Room (`ksp(libs.androidx.room.compiler)` in scaffold) | ✓ (`examples/authenticator/AuthenticatorHomeScreen.kt:37-42`) |
| App-private files | `lightContext.filesDir: File` | ✓ |
| Bundled assets | `lightContext.readAsset("path")` → `ByteArray` (whole file); `LightAudioSource.AssetSource` for audio | ✓ (`examples/audio-demo/ToneScreen.kt:117`) |
| Share files with LightOS | `lightContext.fileShare: LightFileShare` — `read/write/delete/exists/list/getUri` under `filesDir/shared`, served by `LightFileProvider` **only to `SYSTEM_UID`** | Not a user-facing export/share; it is for ringtones/wallpapers etc. |
| Network state | `lightContext.connectivity.currentStatus` / `observeNetworkStatus()` (`isConnected/isWifi/isMetered`; needs `ACCESS_NETWORK_STATE`) | ✓ |
| HTTP | Ktor client + OkHttp (`api` deps of `sdk:client`); needs `INTERNET` | ✓ |
| Talk to LightOS | `callRemoteServiceMethod(method, body, timeout=5s)`; methods: `GetToken, GetVersion, SetRingtone, GetKeyboardOptions, GetUserPreferences(hapticsEnabled), GetPermission, RequestPermissionComponent, DeviceKeyEvent, OpenDialer(phoneNumber)` | ✓ (`LightServiceMethod.kt:38-159`) |
| Runtime permissions | `checkPermission(perm)`, `rememberPermissionRequestLauncher(perm).launch()` (LightOS-hosted prompt) | ✓ |
| Audio | `DefaultLightAudio(sealedActivity)`: `newPlayer(usage, playback=Attached|Detached)`, `newRecorder`, `newCapture` (PCM flow), `newVoice` (PCM one-shot) | ✓; detached needs `capabilities=["detached-audio"]`; service idle-stops after 15 min (`LightAudioService.kt:146`) |
| NFC | `DefaultLightNfc(sealedActivity)`: `availability`, `newReader().asFlow()/awaitTap()`; `LightNfcTapReader` composable | ✓ (needs `android.permission.NFC`; PhoneScoop/Wikipedia say LP3 NFC "not enabled at launch" — availability is checked at runtime) |
| Vibrate / haptics | only the automatic click haptic inside `lightClickable` (and keyboard) | **No custom vibration API for tools** (`LightHapticFeedback` needs a `Context`) |
| Clipboard | — | **None** (no API; `getSystemService` banned) |
| Share / export to other apps | — | **None** (`startActivity` banned; no share method on the server) |
| Notifications | `POST_NOTIFICATIONS` is allow-listed, but there is no API, `NotificationManager` needs `getSystemService`, and LightOS "does not render a notification shade and does not listen for notifications from tool processes" | **Effectively none** (`docs/design_decisions/detached_audio.md:120-126`) |
| Exact alarms / timers | — | **None** (`WorkManager` only, 15-min periodic floor, "aren't great for anything time-sensitive" — client README) |
| File import / "private inbox" / File Manager | — | **None in the SDK.** dupontgu (Jul 7, discussion #70) said a LightOS **File Manager** for WiFi transfers is coming with source "upon LightOS release"; nothing in `sdk/` yet. |
| Sensors / location / Bluetooth | — | **None** (location permissions are allow-listed but no wrapper exists; `LocationManager` needs a Context) |
| Screen orientation lock | `orientation = "portrait"` in toml | ✓ |

### D.4 Storage guidance (as practiced by the examples)

- DataStore for small state (`weather`), Room via `buildDatabase` for records (`authenticator`, `LP3-Bible`), plain files under `filesDir` for downloaded blobs (`LP3-Bible` writes translation JSON there). `LightScreen` VM stores are cleared on pop → shield in-flight writes with `withContext(NonCancellable)` (the sudoku lesson in the project's learning-path doc still applies).
- Nav stack and view models are process-memory only; rebuild from durable storage on relaunch (LightOS re-launches the tool via `componentToRelaunch` after it takes the screen for a key modal — `EmulatorApplication.kt:58-80`).

### D.5 Asset bundling and size limits (the builder is the authority — `builder/lightbuilder/allowlist.py`, `extract.py`)

There is **no APK size limit anywhere in the SDK source or docs.** The limits that exist are on the *source extraction* Light performs before building your tool on their server:

| Rule | Value | Evidence |
|---|---|---|
| Files taken from your repo | only `tool/build.gradle.kts`, `tool/lighttool.toml`, and `tool/src/main/{kotlin,java,res,assets}/**` (tests, `AndroidManifest.xml`, everything else discarded) | `allowlist.py:24-36`; `extract.py:58-107` |
| **Allowed `assets/` extensions** | `.png .jpg .jpeg .webp .gif .svg .json .txt .md .ttf .otf .bin .dat .csv .html .css` | `allowlist.py:17-21` |
| Allowed `res/` extensions | `.xml .png .jpg .jpeg .webp .gif .svg .json .ttf .otf` | `allowlist.py:14-16` |
| Disallowed extension (or no extension) | **aborts the build** (`ExtractionError("extension not allowed in assets/: …")`) — not silently skipped | `extract.py:216-219` |
| **Per-file cap** | **5 MiB** (`MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024`) — abort | `allowlist.py:46`; `extract.py:222-225` |
| Total extracted | 100 MiB | `allowlist.py:49` |
| File count | 10,000 | `allowlist.py:52` |
| Forbidden path parts | `META-INF .git .svn .hg __MACOSX`, symlinks | `allowlist.py:41-44` |
| Build command | `gradle :tool:assembleRelease --offline -DlightSdk.unsigned=true` against a pinned SDK image; outputs unsigned APK + `recipe.json` + `extracted-source.zip` | `builder/README.md` |

Consequences for a large bundled reference dataset: **no `.db`/`.sqlite` (Room `createFromAsset`) and no `.gz`/`.zip`**; ship `.json`/`.csv`/`.bin`/`.dat` chunks each < 5 MiB and import into Room on first run (or search in memory). `examples/audio-demo` itself carries `.mp3/.wav/.ogg` assets that this allow-list would reject — the examples are built in-tree, not through the builder. Also note `res/raw` is not exempt (same `res` list — no `.db` there either), and the dev's `build.gradle.kts` is pre-scanned with a Python mirror of the ban list before Gradle runs (`allowlist.py:58-65`).

### D.6 Trust tiers the phone enforces (`sdk/server/…/LightSdkServerSettings.kt:10-22`, `LightSdkServer.kt:26-34, 57-77`)

`ClientFilterLevel`: `ExcludeAllApks` ("Default Only") · `AllowLightApprovedApks` ("Community Tools", **default**) · `AllowLightSignedApks` ("Built with SDK") · `AllowAllApks` ("All Tools"). `ClientCertType`: `Unknown` · `LightSdkSignedUnverified` · `LightSdkApproved`. A locally-signed dev build is `Unknown` on a real LP3 (only the emulator treats the dev cert as signed — `EmulatorApplication.kt:92-109`), so it is only visible under "All Tools" — matching dupontgu's Jul 24 statement.

---

## (E) Examples map (`examples/`, `settings.gradle.kts:24-37`)

| Example | `id` / version | Demonstrates | Files to read |
|---|---|---|---|
| `ui-demo` | `com.thelightphone.uidemo` 1.0.0 (CAMERA) | `SimpleLightScreen` home list → `navigateTo(::Screen)`; **counter** in a VM `StateFlow` + bottom-bar readout; `LightScrollView`; **read-only `LightTextField` → editor screen returning `String`** (`EditorRequest(title, initialValue, initialCaps)`); QR scan with result → second screen; `LightFullscreenModal`; icons gallery; theme toggle; `LightTouchableProgressBar`; **`Key Events` screen** logging `onKeyDown/Up/Multiple` | `UiDemoHomeScreen.kt`, `UiDemoSecondScreen.kt`, `UiDemoTextInputScreen.kt`, `UiDemoTextInputEditorScreen.kt`, `UiDemoKeyEventsScreen.kt`, `UiDemoProgressBarScreen.kt` |
| `weather` | `com.thelightphone.weather` 5.0.0 (INTERNET) | Ktor + kotlinx-serialization API client; DataStore prefs cache (`WeatherPreferences.kt`); typed location search via editor screen with a **result list**; `onScreenShow` refresh with a `skipRefreshOnNextScreenShow` guard for volume-key relaunches; `onKeyDown` override; `kotlinx-datetime` | `WeatherHomeScreen.kt`, `WeatherViewModel.kt:150-156`, `WeatherApi.kt` |
| `authenticator` | `com.thelightphone.authenticator` 3.0.0 (CAMERA) | **Multi-screen stateful tracker**: `@InitialScreen` list → detail (`AuthenticatorCodeScreen`) → **confirm screen returning `Boolean`** (`AuthenticatorConfirmRemoveScreen`); QR scanner returning `Result<Account>`; **Room** via `lightContext.buildDatabase` behind a repository singleton; keystore-encrypted secrets; `LightFullscreenModal` for errors; `kotlin.test` unit tests | `AuthenticatorHomeScreen.kt:33-48, 88-129`, `TotpDatabase.kt`, `TotpAccountRepository.kt:60-73` |
| `audio-demo` | `com.thelightphone.audiodemo` 0.0.1 (INTERNET, RECORD_AUDIO; `capabilities=["detached-audio"]`; `orientation="portrait"`) | `LightAudio` player (asset/file/URL queues, detached playback), recorder, capture spectrum, PCM voice; **`lightContext::readAsset` injected into a VM**; 2 MB of bundled assets; 7 test files | `PlayerScreen.kt`, `ToneScreen.kt:62-117`, `AudioLibraryRepository.kt` |
| `tool/` scaffold | `com.thelightphone.app` 1.0.0 (INTERNET; `serverPackage=emulator`) | `HomeScreen`/`DetailScreen`/`ToolEntryPoint` skeleton; the build file to copy (`ksp(libs.androidx.room.compiler)` already wired) | `tool/build.gradle.kts`, `tool/lighttool.toml` |

Closest to clone:

- **(a) Large bundled reference dataset with search** — no SDK example bundles data. Compose it from `audio-demo` (`readAsset` + a static catalog object), `weather` (typed search → result list, DataStore), and `authenticator` (Room). Community precedents: `cmg-ops/LP3-Bible` (SDK-built; downloads translation JSON into `filesDir` at runtime, searches in memory, Room only for user data — `bible-src/tool/src/main/kotlin/…/BibleDatabase.kt`, `BibleStore.kt`) and `garado/dictionary` (pre-SDK React Native with a **45 MB `dictionary.db` asset** — would fail the builder on extension *and* size; `dict-src/assets/dictionary.db`).
- **(b) Multi-screen stateful tracker** — `authenticator` (screens returning typed results, repository + Room, confirm screen) plus `ui-demo`'s counter VM.
- **(c) Text notes** — `ui-demo` `UiDemoTextInputScreen` + `UiDemoTextInputEditorScreen` (multi-line by default).

---

## (F) Hardware input findings — the scroll wheel and buttons **are** reachable by tools (with caveats)

**Evidence chain (all source):**

1. `LightKeyHandler` (`sdk/ui/…/LightKeyHandler.kt:5-15`) is implemented by both `SimpleLightScreen` (`LightScreen.kt:11-12`) and `LightViewModel` (`LightViewModel.kt:6`); `LightScreen` forwards to its VM (`LightScreen.kt:95-103`).
2. `LightActivity.onKeyDown/onKeyUp/onKeyMultiple` (`LightActivity.kt:152-197`): `KEYCODE_BACK`/`KEYCODE_HOME` go to `super` ("home button won't get dispatched to external tools"); otherwise the **current screen gets first refusal** (return `true` to consume); if unconsumed **and** `keyCode ∈ LightDeviceKeys.mapping`, the event is forwarded to LightOS via `LightServiceMethod.DeviceKeyEvent` (`keyCode, repeatCount, action, characters, unicodeChar, componentToRelaunch`) — added 2026-07-24 (commits `2fc97a7`, `101eb68`, `52f1e99`).
3. `LightDeviceKeys` (light-keyboard `v0.0.18`, `ui/src/main/java/com/thelightphone/lp3Keyboard/ui/HardwareKeyboardInput.kt:18-35`, doc-comment "Key codes reported by the hardware input on an LP3"):

   | LP3 control | Android keyCode | Enum |
   |---|---|---|
   | Volume up / down (right side) | 24 / 25 | `VolumeUp` / `VolumeDown` |
   | Camera button full press | 27 (`KEYCODE_CAMERA`) | `ShutterPressed` |
   | Camera button half press | 80 (`KEYCODE_FOCUS`) | `ShutterHalfPressed` |
   | **Scroll wheel turn up** | **317** | `RotaryTurnUp` |
   | **Scroll wheel turn down** | **318** | `RotaryTurnDown` |
   | **Scroll wheel click** | **319** | `RotaryButtonPress` |

   (317–319 are `KEYCODE_EMOJI_PICKER/SCREENSHOT/DICTATE` in AOSP naming; LP3's key layout repurposes them. The same file notes LP3's keyboard layout remaps external-HID `T`/`R` as wheel events — `lightOsRemap`.)
4. Server side: `LightSdkServer.onDeviceKeyEvent` "Handle a hardware key event forwarded from a client whose current screen did not consume it" (`sdk/server/…/LightSdkServer.kt:150-160`). The emulator shows a volume modal, **foregrounds itself over the tool, then relaunches the tool** via `componentToRelaunch` (`EmulatorApplication.kt:58-88`) — so an unconsumed wheel/volume press costs the tool an `onAppPause` → `onScreenShow` round-trip (the weather VM works around exactly this: `WeatherViewModel.kt:150-156`).
5. LightOS side (official changelog): **v572, 20 Aug 2026 — "Fixes all hardware button events for external tools to LightOS (e.g. volume up/down, camera/flashlight within Weather or Authenticator tools)"** (support.thelightphone.com › Software Versions Change Log). LightOS's own wheel semantics: "controls the brightness (and will have other control options in future updates)"; "Pressing the wheel turns on and off the flashlight" (support › Light Phone III Introduction). Wheel is on the **left** side; volume/menu/camera on the right; power (with fingerprint) on top.
6. Demo: `examples/ui-demo/…/UiDemoKeyEventsScreen.kt` logs every key ("BACK/HOME never arrive here — LightActivity short-circuits them before the screen"); the emulator only maps `VolumeUp/VolumeDown` (`EmulatorDeviceKeyHandler.kt:42-49`), so wheel events cannot be exercised on the AVD except via an external keyboard producing key codes 317–319.

**Plain statement:** the SDK has a first-class hardware-key path and enumerates the LP3 wheel's three key codes; a `LightViewModel` override of `onKeyDown` can turn wheel ticks into HP ±1 or list scrolling. **Not verifiable from source:** whether retail LightOS lets key codes 317/318/319 reach a foreground tool's window at all (LightOS is closed; the v572 note names volume, camera and flashlight — i.e. the wheel *click* — but not wheel *turns*). Consuming the wheel also suppresses LightOS brightness/flashlight while the tool is open, which reviewers may treat as hijacking a system control. Verify on hardware with the `ui-demo` Key Events screen before designing around it (open question J.1). No `rotary`, `wheel`, or `dial` token appears anywhere in `light-sdk` (`grep -rniE "rotary|wheel|dial"`), so nothing in the SDK is "scroll-wheel-aware" beyond raw key codes.

---

## (G) Tool Library submission status (29 Aug 2026)

**Status: not open.** Evidence, in order of authority:

- `README.md` (unchanged since July): "**As of July 1, 2026, there's no 'easy' way to share your tool with a Light Phone III user. We're working hard on that.** … In the near future, you'll be able to queue up a build of your tool on our servers, and if it follows our guidelines and compiles cleanly, we will hand you back a signed, shareable APK." Also the only in-repo statement of criteria: "**we're going to be looking pretty hard at whether a submitted tool matches the Light ethos both functionally and aesthetically. We've included a UX/UI library to make this as easy as possible!**"
- Repo contains **no submission template, checklist, or design-guidelines page** (`.github/` holds only `workflows/pr-check.yml`; `docs/` has repo, tool_metadata, system_app, design_decisions). `CONTRIBUTING.md` covers SDK contributions only (issues before PRs; maintainers green-light; `./gradlew check`; human-written communication; "You are responsible for any code or other communication that comes from your account"). `docs/tool_metadata/README.md` implies a build server exists: "The Light build server will reject a submission whose `versionCode` is not greater than the previous published build" and "If your tool is going in the Light tool library, this [id] will need to be globally unique! We'll let you know if you're trying to pick one that's already been used."
- `builder/README.md`: the server pipeline exists in source ("**This is how Light will build your source into a Light-signed APK**"); TODO list still reads "Tools should be public. Eventually, tools will only be buildable if they are public."
- developers.thelightphone.com (fetched 29 Aug 2026) — the review criteria, verbatim:
  - "The Tool Library will only include tools that are blessed by Light, vetted and supported by the community and adhering to the Light ethos."
  - "Each tool must serve a clear intentional purpose, and of course, respect user-privacy to the fullest extent."
  - "The idea is to provide a curated, non-commercial, open-source platform of user-created tools that expand the utility of the device."
  - "There will be a submission process which will be reviewed by both the community as well as the Light Team. In order for us to make a tool available for the larger user base through the Tool Library we will ensure that the tool follows all of our existing guidelines (i.e. no social media, internet browser, news, email or other infinite feeds). The tool must respect user privacy. It is also important that it maintains a similar UX and intentional design as our existing tools. These principles will be explicitly laid out in our developer tools and submission process. This is important for maintaining the trust our users have put in Light."
  - "Developers can choose to submit their custom tools to be vetted by the community and Light team to ensure there is nothing malicious and that it is consistent with the ethos and intentional aesthetic of LightOS. Once accepted Light users will be able to install your tool directly from the Tool Library in the dashboard."
  - Timeline: "In May, we'll send invitations for our GitHub environment … **By early Fall we expect to have the Tool Library go live to all end users.**"
  - "This is not an app store; the tools will be shared freely in the Tool Library. We do plan to include an option to tip the creators…"
  - "Do I need to share my tools with the library? No, of course not."
  - No separate design-guidelines page exists; the FAQ says the principles "will be explicitly laid out" later.
- Light team, GitHub discussion #121 (dupontgu, 24 Jul 2026): dashboard install flow "rough estimate: late august", after the LightOS release following v568; today "the only way … is by flipping the setting in Developer settings to 'All Tools' and then adb'ing your local tool in." Discussion #93: Developer Mode tiers = baked-in only / signed **and** in the community library (default) / signed by Light / any APK.
- LightOS changelog: v568 (15 Jul) shipped Weather & Authenticator "our first tools built using the new SDK"; **v572 (20 Aug)** shipped hardware-button forwarding fixes and "Add ability to view third party tools and their versions in Settings menu" — **no dashboard install, no Tool Library**.
- awesome-light (unofficial registry, 73 apps, rebuilt 27 Aug): "**Light Approved: 0**" — no tool has yet been blessed. Its own curation text: "**Light Approved** — The highest bar. These tools have been blessed by Light."

Working implication: build to the criteria above (intentional purpose, privacy, no feeds, Light UX/aesthetic via `sdk:ui`, open source, non-commercial, public repo, semver, unique `id`), keep `tool/` buildable by the `builder/` extractor, and expect the formal checklist to appear with the submission flow in September.

---

## (H) Ecosystem / collision findings

**Direct collisions (Light Phone D&D / TTRPG / character-sheet / dice / initiative tools): none found.**

| Source checked | Result |
|---|---|
| `garado/awesome-light` (73 apps, commit `a070a50`, 27 Aug 2026) — grep of every `content/apps/*/index.md` for dnd/d&d/dungeon/dice/ttrpg/tabletop/character sheet/initiative/pathfinder/rpg/spell | Only hit: **Passatempo** (tyshi00, Entertainment, SDK-built, added 2026-07-11) — "Eight things to do: Snake, Brick Breaker, Pong, Tic-Tac-Toe, Connect Four, Sudoku, Word Search, and **Dice**." A generic dice mini-game inside a games bundle, not a TTRPG tool. Categories: Utility 18, Music/Audio 11, Productivity 9, Reading & Reference 8, Navigation 7, Health 5, Entertainment 4 (Backlog, Chess, LightSolitaire, Passatempo), … |
| awesome-light live site | No D&D/RPG/tabletop mention; "Light Approved 0". |
| GitHub topics `light-phone` (4 repos), `lightphone` (16), `lightos` (7), `light-sdk` (1) | No D&D/TTRPG/dice repos. Game-related only: `tyleryancey/light-sudoku`, `tyleryancey/light-chess`, `anweaver23` Chess, `gi-os/BrightSolitaire`. |
| GitHub org discussions (26 threads listed, Jul 2026) | No D&D/dice/RPG requests. Nearest "game" threads: Chess design-intent check, Passatempo. |
| Web search (multiple phrasings, Google/Bing-style index) | Zero Light-Phone-specific D&D results; only mainstream apps (D&D Beyond, DnDice, Roll & Play). |
| Reddit r/LightPhone, r/dumbphones | **Could not be queried from this session** — `reddit.com`, `old.reddit.com`, redlib mirrors and the PullPush archive are all rejected by the egress proxy (HTTP 403). Search-engine indexing of those subreddits returned no D&D/dice/character-sheet posts. Treat as *unverified*, not *absent*; manual check URL: `https://www.reddit.com/r/LightPhone/search?q=dnd+OR+dice+OR+%22character+sheet%22+OR+ttrpg&restrict_sr=1&sort=new`. |
| GitHub code/repo search | Not available in this session (search API is blocked; topics pages used instead). |

Adjacent precedents worth knowing:

- **Offline reference + search:** `garado/dictionary` (RN, 45 MB `.db`, Editor's Pick), `cmg-ops/LP3-Bible` (SDK; runtime-downloaded JSON, full-text search), `tyshi00/light-translator` (offline, 200 languages), `chopindavid/recall` (Anki review-only client). These set the community's expectation that reference tools work offline.
- **Trackers with counters/toggles:** `wsturgiss/light-training` (sets/reps), `tyshi00/tracker` (water/sleep), `jabberbox/pokey`, `cmg-ops/lists` (nested checklists, A/B/C priority), `zacksimpson/composer` (Markdown notes).
- **Name/ID hygiene:** two "Passes", two "Radio", three "Bible" tools already coexist; `tyshi00/Ledger` shares a name with `tyleryancey/light-ledger`. Pick a distinctive `label` and a reverse-DNS `id` you control — `id` is permanent and must be globally unique in the library (`docs/tool_metadata/README.md`).
- **Ethos signals reviewers reward:** Chess author posted a design-intent check before building; Passatempo enforces per-game daily budgets; several Editor's Picks are single-purpose, offline, account-free. An "infinite feed" is the explicit disqualifier; a session-bounded companion (sheet + spells + notes + initiative) is far from that line.

Unclaimed: a D&D/TTRPG character sheet, spell reference, initiative/HP tracker, or dedicated dice roller for the LP3 — nothing exists or is announced in any indexed venue checked.

---

## (I) Light Phone III hardware facts relevant to UI design

| Fact | Value | Source |
|---|---|---|
| Display | 3.92" AMOLED, **1080 × 1240**, ~419 ppi, ~1:1.15 aspect (PhoneScoop rounds to "1:1"); monochrome UI on a color panel | SDK `README.md` Quickstart ("1080 X 1240, 3.92\" display"); `docs/system_app/README.md` §1; Wikipedia LP3; PhoneScoop |
| Grid | 27 × 31 grid units (`LightGrid`), 1 unit ≈ 40 px ≈ screenWidthDp/27 | `LightGrid.kt:13-16` |
| Type scale | multiplied by `screenHeightDp/600` (`designVerticalPxToSp`) | `LightGrid.kt:30-36`, `LightText.kt:57-73` |
| Dimensions / weight | 106 × 71.5 × 12 mm; 124 g | Wikipedia; PhoneScoop (4.17 × 2.81 × 0.47 in, 4.37 oz) |
| SoC / RAM / storage | Qualcomm SM4450 (Snapdragon 4 Gen 2); 6 GB; 128 GB non-expandable | Wikipedia; PhoneScoop |
| Battery | 1,800 mAh, user-replaceable; no fast/wireless charging | PhoneScoop (Wikipedia omits capacity) |
| **Scroll wheel** | Clickable wheel on the **left** side. Turn = brightness ("will have other control options in future updates"); press = flashlight on/off. Key codes 317/318/319 (§F) | support › Light Phone III Introduction; light-keyboard `HardwareKeyboardInput.kt:21-35` |
| Right-side buttons | Volume up/down (24/25); larger middle **Menu** button (long-press returns to the active background tool — calls/directions/music/podcasts); two-step camera button (half-press 80 = focus/exposure lock, full 27 = capture) | support › LP3 Introduction; PhoneScoop ("volume, menu, camera (two-step) on right") |
| Power / fingerprint | Power button on top with stand-alone fingerprint sensor; "fingerprint ID is not yet enabled on the LightOS" | support › LP3 Introduction; PhoneScoop |
| NFC | Hardware present; "not enabled at launch" (PhoneScoop) / "Hardware only" (Wikipedia). SDK now ships an NFC reader API + `android.permission.NFC` allow-listed; `LightNfc.availability` reports `Unsupported/Disabled/PermissionMissing/Ready` at runtime | `sdk/client/…/nfc/LightNfc.kt:27-34` |
| Ports / radios | USB-C 2.0 (audio + media transfer), no headphone jack, eSIM-only, 5G/LTE, Bluetooth 5.0, Wi-Fi + hotspot (PhoneScoop wrongly lists Wi-Fi "Not available" — LightOS has a Hotspot tool and the SDK reports `isWifi`) | PhoneScoop; support changelog v566; `LightConnectivity.kt:33` |
| Cameras | 50 MP rear (binned 12 MP) with LED flash; 8 MP front (disabled at launch) | Wikipedia; PhoneScoop |
| Haptics | Present; "LP3, which has a 'slow' motor" — SDK click = 45 ms one-shot; user can disable haptics system-wide | `LightHapticFeedback.kt:11-12`; `GetUserPreferences.hapticsEnabled` |
| Notification shade | None — LightOS renders no shade and ignores tool notifications | `docs/design_decisions/detached_audio.md:122-126` |
| Keyboard | No system IME for tools; text entry is the **full-screen** `LightTextInputEditor` with the embedded Compose LP3 keyboard (English/QWERTY only; swipe optional; emoji page; voice key shown when LightOS says so). Bluetooth/USB HID keyboards route through `Modifier.hardwareKeyboardInput` in the keyboard lib | `LightTextInputEditor.kt`; light-keyboard `README.md`; `HardwareKeyboardInput.kt:59-127` |
| Emulator parity | AVD: API 34, no Play services, 1080×1240 @ 3.92"; LightOS emulator as a `priv-app` with the AOSP platform key; wheel not emulated | `docs/system_app/README.md` |
| LightOS version | v572 (20 Aug 2026) | support › Software Versions Change Log |

---

## (J) Open questions that need a Light maintainer (or hands-on hardware) answer

1. **Wheel events on retail LightOS:** do key codes 317/318/319 reach a foreground tool's `LightActivity` on v572, or does LightOS intercept wheel turns for brightness before dispatch? If they arrive, is a tool consuming them (thereby disabling brightness/flashlight while open) acceptable for Tool Library review? (Check first with `ui-demo` → "KEY EVENTS" on a real LP3.)
2. **Bundled data formats:** will `.db`/`.sqlite` (Room `createFromAsset`) or compressed archives be added to `ALLOWED_ASSET_EXTENSIONS`, and is the 5 MiB per-file cap negotiable? Today a 5e reference set must ship as < 5 MiB `.json/.csv/.bin/.dat` chunks.
3. **APK/download size policy:** none exists in source or docs — is there an unpublished limit at signing or dashboard install?
4. **Submission mechanics:** when does the dashboard build/sign queue open (dev site: "early Fall"; dupontgu: "late august" for install), what does the submission form ask for, and is `versionCode` monotonicity enforced across rejected resubmits as `docs/tool_metadata` states?
5. **Design guidelines:** the FAQ promises principles "explicitly laid out in our developer tools and submission process" — is a written checklist coming with the form?
6. **Haptics for tools:** `LightHapticFeedback` requires a `Context` tools cannot obtain; is a Context-free `vibrate(duration)` (e.g. via `SealedLightContext`) planned? (Discussion #95 implied tool-level access.)
7. **Back semantics:** should the Activity-level back path consult `LightViewModel.onBackPressed()`? Is any system back gesture reachable on LP3 at all, or is on-screen BACK the only path (in which case the README's "back bar" and dispatcher wording should be corrected)?
8. **Notifications:** confirm that `POST_NOTIFICATIONS` is effectively inert for tools (no shade, no LightOS listener) and whether LightOS-surfaced alerts for tools are planned.
9. **Clipboard / share / file import:** any planned API? The forthcoming LightOS File Manager (WiFi transfer) — will tools get a read path to imported files (e.g. a homebrew JSON)?
10. **`LightLazyScrollView` with variable-height rows** — planned, or is `LightScrollView` (non-lazy) the intended path for mixed-height lists such as spell cards?
11. **`serverPackage`:** a build is bound to either `com.lightos` or `com.thelightphone.sdk.emulator` — will the server package be resolved at runtime so one APK works on both?
12. **Doc drift to report upstream:** README's `LightScreenViewModel` (class is `LightViewModel`); client README's "excluding [@InitialScreen] … will fail the build" (it fails at runtime); "renders a back bar" (it does not); per-screen `dataStore/filesDir/fileShare` are on `lightContext`, not the screen; `builder/README.md` toml schema omits `capabilities`/`orientation`; `examples/audio-demo` assets violate the builder's own asset allow-list.
