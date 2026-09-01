package dev.tyler.grimoire.ui.common

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import dev.tyler.grimoire.ui.keys.WheelHandler

/**
 * The one full-screen text editor (docs/ARCHITECTURE.md reserves `ui/common` for it; docs/UI-SPEC.md S13.3 is
 * its first caller, and M3/M4 reuse it for character and journal names). It is a thin round trip: push it with
 * a [title] and an [initial] value, get the typed string back through `navigateTo`'s result callback.
 *
 * `LightTextInputEditor` draws its own top bar, its own keyboard and its own bottom bar, so it is the only
 * thing this screen composes — a `Column`, a bar or a padding of ours here would push the SDK's own chrome off
 * the canvas. The `modifier` pins the ground explicitly; `LightTheme` already maps Material's `surface` onto
 * the Light background (LightTheme.kt:174-199), which is what the editor's internal `Surface` paints with.
 *
 * Three facts make this correct, and all three are easy to get wrong:
 *
 * 1. **`singleLine = true` is what makes the Return key submit.** The keyboard's Return handler reads
 *    `if (singleLine) onReturn() else insertAtCursor("\n")`
 *    (`sdk/ui/.../keyboard/TextInputKeyboardCallback.kt:34`). Left false, Return would type a newline into a
 *    field whose text area does not scroll, and only the bottom bar's SUBMIT button would end the edit.
 * 2. **A cancel delivers nothing at all.** `LightActivity`'s back dispatcher calls `activity.goBack()` straight
 *    (LightActivity.kt:139-146), never this screen's `goBack`, so `result` stays null; and `deliverResult` is
 *    `val result = screen.result ?: return` (LightActivity.kt:52-55), so a null result never reaches the
 *    callback. The hardware back button, the drawn BACK button and a `goBack(null)` are therefore
 *    indistinguishable to a caller: **"the callback never fired" is the cancel signal**. The result type is
 *    [String] with a nullable parameter so that the cancel path is expressible at both ends and every caller is
 *    forced to null-check what it does get.
 * 3. **The editor has to swallow the wheel like every other screen.** This is a [SimpleLightScreen], so there
 *    is no view model to carry the repo's `handleKey` contract and the three [com.thelightphone.sdk.ui.LightKeyHandler]
 *    methods default to false (LightKeyHandler.kt:6-14). Left at the default, `LightActivity` hands every key
 *    in `LightDeviceKeys.mapping` to `forwardKeyEventToServer(..., componentToRelaunch = …)`
 *    (LightActivity.kt:157-166,186-197,199-216) — so a thumb resting on the wheel while typing would foreground
 *    LightOS and relaunch the tool mid-query. All three halves are consumed here as no-ops (the editor defines
 *    no wheel gesture of its own) through the same [WheelHandler.consumes] the compendium view models use, which
 *    leaves volume and camera unconsumed. The composed keyboard still sees any key it wants first: `LightActivity`
 *    overrides `onKeyDown`, not `dispatchKeyEvent`, so the view hierarchy gets first refusal and this override
 *    only ever sees what nothing on screen claimed.
 *
 * [initialCaps] defaults to false, which is what S13.3 wants: it types a search query, not a name, and the
 * search lowercases everything it is given anyway. The UI-SPEC component table's `initialCaps = true` is the
 * *name* recipe, and S0's `NEW` is its first caller — passed here rather than flipped under FIND, so the two
 * callers keep their own keyboards.
 */
class TextEditorScreen(
    sealedActivity: SealedLightActivity,
    private val title: String,
    private val initial: String = "",
    private val initialCaps: Boolean = false,
) : SimpleLightScreen<String?>(sealedActivity) {
    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        LightTheme(colors = colors) {
            LightTextInputEditor(
                title = title,
                state = rememberTextFieldState(initial),
                onSubmit = { goBack(it.toString()) },
                onBack = { goBack(null) },
                keyboardOptionsFlow = rememberKeyboardOptions(),
                modifier = Modifier.background(LightThemeTokens.colors.background),
                singleLine = true,
                initialCaps = initialCaps,
            )
        }
    }

    /**
     * Every wheel key, swallowed without acting — see fact 3 above for why the release halves matter as much as
     * the press. Kept on the bare key code, the repo-wide convention, because `android.view.KeyEvent` cannot be
     * constructed in a JVM test.
     */
    private fun consumesKey(keyCode: Int): Boolean = WheelHandler.consumes(keyCode)

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = consumesKey(keyCode)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = consumesKey(keyCode)

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean = consumesKey(keyCode)
}
