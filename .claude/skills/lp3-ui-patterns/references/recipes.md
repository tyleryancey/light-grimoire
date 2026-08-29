# Recipe sketches (Kotlin, sdk:ui only)

These are shapes, not drop-in code — verify signatures against `sdk/ui` at the current
checkout (`sdk-verifier` agent) before use.

## Row list with a bounded list

```kotlin
LightScrollView(modifier = Modifier.fillMaxSize()) {
    rows.take(MAX_ROWS).forEach { row ->
        Row(
            Modifier.fillMaxWidth().height(2.5f.gridUnitsAsDp())
                .lightClickable { onOpen(row.id) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LightText(row.title, LightTextVariant.Copy, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            LightIcon(LightIcons.ARROW_RIGHT)
        }
    }
}
```

## Pip strip

```kotlin
@Composable fun PipStrip(value: Int, max: Int, onSpend: () -> Unit, onRestore: () -> Unit) {
    Row {
        repeat(max) { i ->
            val filled = i < value
            LightIcon(
                if (filled) LightIcons.CIRCLE else LightIcons.STAR_OUTLINE, // hollow glyph = spent
                Modifier.lightClickable { if (filled) onSpend() else onRestore() },
                width = 1.5f, height = 1.5f,
            )
        }
    }
}
```
(If a true hollow-circle glyph is wanted, draw it with `Canvas` — a 1-unit ring stroked in
`colors.content`; no bitmap assets.)

## Number pad with wheel

```kotlin
class HpViewModel : LightViewModel<Unit>() {
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        317 -> { nudge(+1); true }   // LightDeviceKeys.RotaryTurnUp
        318 -> { nudge(-1); true }   // RotaryTurnDown
        319 -> { commit(); true }    // RotaryButtonPress
        else -> false                // let LightOS have volume/camera
    }
}
```
Prefer the `LightDeviceKeys` enum from the keyboard library over literals once its import
is verified in this repo; keep the literals in a comment so the mapping is greppable.

## Editor round trip

```kotlin
// caller
LightTextField(label = "NAME", value = state.name, placeholder = "Tap to name") {
    navigateTo(::NameEditorScreen) { result -> result?.let(viewModel::setName) }
}
// editor screen
class NameEditorScreen(a: SealedLightActivity) : SimpleLightScreen<String?>(a) {
    @Composable override fun Content() {
        val state = rememberTextFieldState(initialValue)
        LightTextInputEditor(
            title = "NAME", state = state, keyboardOptionsFlow = rememberKeyboardOptions(),
            singleLine = true, initialCaps = true,
            onSubmit = { goBack(it.toString().trim().take(40)) }, onBack = { goBack(null) },
        )
    }
}
```

## Transient roll modal

```kotlin
class RollResultModal(private val roll: Roll) : LightModal { … Content(): total in Subtitle,
    dice in Detail, "CRIT"/"MISS" beneath a natural 20/1 … }
LightModalManager.show(RollResultModal(roll), duration = 2.seconds)
```

## Confirm screen returning Boolean

```kotlin
class ConfirmScreen(a: SealedLightActivity, private val message: String) : SimpleLightScreen<Boolean>(a) {
    @Composable override fun Content() { … LightBottomBar(listOf(
        LightBarButton.Text("CANCEL") { goBack(false) }, null, LightBarButton.Text("CONFIRM") { goBack(true) })) }
}
```
