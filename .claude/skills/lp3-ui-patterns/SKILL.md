---
name: lp3-ui-patterns
description: Screen recipes for Grimoire built only from the Light SDK's sdk:ui primitives (verified 29 Aug 2026, commit 3df3c24) — rows, pip strips, toggles, number pads with wheel support, editor round-trips, confirm screens, transient roll modals, long-text readers, searchable lists — plus grid/typography numbers and the LightScreen/ViewModel lifecycle rules. Load when writing or reviewing any Compose file under tool/src/main/kotlin/**/ui/.
user-invocable: false
---

# LP3 UI patterns (sdk:ui only)

Facts first (source-verified; see `docs/research/04-light-sdk-state.md` §C for the full
inventory): the SDK ships `LightText`, `LightTheme`, `LightTopBar`, `LightBottomBar`,
`LightBarButton`, `LightIcon`/`LightIcons` (106), `Modifier.lightClickable` (no ripple,
45 ms haptic), `LightScrollView`, `LightLazyScrollView` (uniform row height only),
`LightTextField` (display-only), `LightTextInputEditor` (full-screen; `singleLine`,
`initialCaps`; text area does not scroll), `LightFullscreenModal(message, onClose)`,
`LightModal`/`LightModalManager` (transient, 2 s default), `LightProgressBar`,
`LightTouchableProgressBar`, `LightQrCodeScanner`, `LightNfcTapReader`, `LightKeyHandler`.
**Not present:** switch, checkbox, radio, tabs, divider, stepper, dialog with content, inline
text field, search box, chips. Build them from `LightText` + `lightClickable` + glyphs.

Grid: `LightGrid.WIDTH = 27`, `HEIGHT = 31`; size with `Float.gridUnitsAsDp()`. Top bar 3
units; bottom bar 4 units; default icon 2 units. Bottom bar ≤ 5 items (≤ 3 if any is text).
Type scale ×(screenHeightDp/600) ≈ 0.79 on the LP3: `Title` 115 → ~90 sp, `Subtitle` 52,
`Heading` 38, `Subheading` 30 (letter-spaced), `Copy` 30 (×1.5 lh), `Button` 30 medium,
`Paragraph` 24.5, `Detail` 20, `Fine` 25, `Superfine` 16, `Micro` 8 (all unscaled sp).
Colours: `LightThemeTokens.colors.background/content/contentSecondary` only.

## Recipes (details and code in `references/recipes.md`)

| Need | Recipe |
|---|---|
| Navigating row | `Row(Modifier.lightClickable { navigateTo(::NextScreen) }.height(2.5f.gridUnitsAsDp()))` with `LightText(Copy)` + trailing `LightIcon(ARROW_RIGHT)` |
| Pip strip (slots/uses) | `Row { repeat(max) { LightIcon(if (i < value) CIRCLE else STAR_OUTLINE …) } }` — filled vs hollow; tap a filled pip = spend, tap the label = restore |
| Toggle row | leading `LightIcon(TOGGLE_STATE_ON/OFF)` or `SELECT_ON/OFF`, whole row clickable |
| Number pad | `LightText(Subtitle)` value, glyph buttons `UP`/`DOWN` (or ±1/±5/±10 text buttons), wheel → `onKeyDown(317/318)` nudges the *focused* number; press (319) = primary action |
| Text entry | `LightTextField(label, value, placeholder) { navigateTo(::NameEditorScreen) { result -> … } }`; the editor screen hosts `LightTextInputEditor(singleLine = true, initialCaps = true)` and returns `String` |
| Confirm | `class ConfirmScreen(…) : SimpleLightScreen<Boolean>` with CONFIRM/CANCEL in the bottom bar; caller `navigateTo(::ConfirmScreen) { ok -> }` |
| Transient result | `LightModalManager.show(RollResultModal(roll), duration = 2.seconds)`; no hold affordance — keep the last result inline on the originating row |
| Long text | `LightScrollView { LightText(text, Paragraph) }`; wheel scrolls via `scrollState.animateScrollBy(±lineHeightPx)` |
| Search | `FIND` in the top bar → editor screen → `LightLazyScrollView(uniformItemHeightGridUnits = 2.5f)` of ≤ 50 results |
| Empty state | one `LightText(Copy, lighten = true)` line; never an illustration |
| Section header | `LightText(Detail, letter-spaced uppercase, lighten = true)` — no dividers |

## Lifecycle rules (client framework)

- One `@InitialScreen` (`HomeScreen(SealedLightActivity)`); screens extend `LightScreen<R, VM>`
  or `SimpleLightScreen<R>`; navigate with `navigateTo(::Screen) { result -> }`; `goBack(result)`.
- `willShow`/`onScreenShow` fire on navigation **and** on `onResume` (LightOS takes the screen
  for volume/wheel modals and calls) — reload state there; guard expensive work with a
  "skip next" flag like the Weather example.
- Draw BACK yourself in `LightTopBar`/`LightBottomBar`; the SDK draws no back bar.
- `onKeyDown` returns `true` only when the screen consumed the key; unconsumed LP3 keys are
  forwarded to LightOS (which then relaunches the tool → another `onScreenShow`).
- Persist in the view model: debounce 400 ms, `withContext(NonCancellable)`; popping a
  screen clears its `ViewModelStore` synchronously.
- No `Context` anywhere; `lightContext.dataStore / filesDir / readAsset / buildDatabase` only.
