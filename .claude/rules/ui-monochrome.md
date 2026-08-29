---
paths:
  - "tool/src/main/kotlin/**/ui/**/*.kt"
---

# ui/ discipline (Light ethos, sdk:ui only)

- Colours only from `LightThemeTokens.colors`; state by weight/glyph; no `Color(` literals.
- Only `sdk:ui` components plus plain Compose layout (`Row`/`Column`/`Box`/`Canvas`). No
  Material widgets, no `LazyColumn` outside `LightLazyScrollView`, no WebView.
- Text entry only via an editor screen hosting `LightTextInputEditor` (`singleLine`,
  `initialCaps` for names); `LightTextField` is display-only.
- Every list bounded; bottom bar ≤ 5 items (≤ 3 if any is text); BACK drawn by us.
- Screens hold no rules logic: render `Derived`, dispatch events to the view model.
- `onScreenShow` reloads from the repository; saves are debounced + `NonCancellable`.
- Match the wireframe in `docs/UI-SPEC.md`; if the design changes, change the spec first.
