---
name: new-screen
description: Scaffold a new Light screen for Grimoire following the house pattern (LightScreen + LightViewModel + typed result + reload-on-show + fixture-backed test) — "/new-screen Conditions" or "/new-screen NameEditor String"
disable-model-invocation: true
argument-hint: "<ScreenName> [ResultType]"
allowed-tools: Read, Grep, Glob, Write, Edit, Bash(./gradlew :tool:testDebugUnitTest*)
---

Scaffold screen **$0** (result type: `$1`, default `Unit`).

Before writing anything, read `docs/UI-SPEC.md` for the screen's wireframe (if it has
none, stop and run the `mono-designer` agent first — no screen without a spec), and
`.claude/skills/lp3-ui-patterns/SKILL.md` for the recipes.

Create, under `tool/src/main/kotlin/dev/tyler/grimoire/ui/<screen>/`:

1. `$0Screen.kt` — `class $0Screen(a: SealedLightActivity) : LightScreen<$1, $0ViewModel>(a)`
   with `viewModelClass = $0ViewModel::class.java` (bare `::class.java` is allowed by the
   scan; never `.java.name`), `createViewModel()`, and `Content()` = `LightTheme { Column {
   LightTopBar(BACK · "<NAME>" · action?) ; body ; LightBottomBar(≤ 5) } }`. BACK calls
   `goBack(null-or-default)`. No colour literals, no Material widgets.
2. `$0ViewModel.kt` — `class $0ViewModel(private val repo: …, private val now: () -> Long = System::currentTimeMillis) : LightViewModel<$1>()`
   exposing `StateFlow<$0UiState>`; `onScreenShow` reloads from the repository; every
   mutation is a function that publishes one new state and schedules a debounced,
   `NonCancellable` save; `onKeyDown` handles 317/318/319 only if the spec gives the wheel a
   job here, returning `true` only when consumed.
3. `$0UiState.kt` — an immutable data class; nothing derived is stored (call `Derive`).
4. `tool/src/test/kotlin/dev/tyler/grimoire/ui/<screen>/$0ViewModelTest.kt` — `kotlin.test`,
   fake repository, virtual clock, one test per user action in the spec, messages LAST in
   asserts.

Then: wire navigation from the parent screen named in the spec (`navigateTo(::$0Screen) { … }`),
add the screen to the navigation map in `docs/UI-SPEC.md` if new, run
`./gradlew :tool:testDebugUnitTest`, and list what the human should check on the emulator
(`run-light-tool`). Do not implement rules logic in the screen — call `Ledger`/`Derive`.
