package dev.tyler.grimoire.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightLazyScrollView
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import dev.tyler.grimoire.compendium.CompendiumStore
import dev.tyler.grimoire.ui.common.NavRow
import dev.tyler.grimoire.ui.common.QuietLine
import dev.tyler.grimoire.ui.common.ROW_HEIGHT_GRID_UNITS
import dev.tyler.grimoire.ui.common.WheelScrollEffect

/**
 * The second half of S0's `NEW`: pick a class, and the character exists (docs/UI-SPEC.md S12 step 2, and
 * S0's `NEW` row).
 *
 * **It pops before anything is created, which is the spec's rule for every chooser** ("Choosers, confirms
 * and search never sit under what they lead to"): this screen's result is a class key, `LightActivity`
 * delivers it to Home only once this screen is off the stack, and Home is what writes the character and
 * pushes S1. So the finished branch is S0 → S1 → S3 and never S0 → picker → S1.
 *
 * A cancel — the drawn `BACK` or the hardware back button — delivers no result at all (`deliverResult` is
 * `val result = screen.result ?: return`), so Home's callback simply never runs and nothing is created.
 * That is the same contract `TextEditorScreen` documents, and it is why the name is asked for first: a
 * player who backs out of either step leaves nothing behind.
 *
 * The view model is built from [CompendiumStore.reader], which throws unless the import is Ready, and
 * `createViewModel` runs inside `navigateTo` — so `NEW` is drawn only in Home's Ready body, which is what
 * keeps this screen unreachable before then.
 */
class ClassPickerScreen(
    sealedActivity: SealedLightActivity,
) : LightScreen<String, ClassPickerViewModel>(sealedActivity) {
    override val viewModelClass: Class<ClassPickerViewModel>
        get() = ClassPickerViewModel::class.java

    override fun createViewModel(): ClassPickerViewModel = ClassPickerViewModel(CompendiumStore.reader())

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()
        // Not `rememberLazyListState()`: the SDK composes every screen at one un-keyed call site, so a
        // pushed screen can land on the slots of the one it covers and inherit its scroll offset. Keyed on
        // the view model — one per back-stack entry — this list opens at the top.
        val listState = remember(viewModel) { LazyListState() }
        val rowHeightPx = with(LocalDensity.current) { ROW_HEIGHT_GRID_UNITS.gridUnitsAsDp().toPx() }
        WheelScrollEffect(viewModel.ticks, listState) { rowHeightPx }
        LightTheme(colors = colors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text("CLASS"),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    if (state.loading) {
                        QuietLine("Opening…")
                    } else {
                        LightLazyScrollView(
                            listState = listState,
                            uniformItemHeightGridUnits = ROW_HEIGHT_GRID_UNITS,
                        ) {
                            items(state.rows.size) { index ->
                                val row = state.rows[index]
                                NavRow(name = row.name, onClick = { goBack(row.key) })
                            }
                        }
                    }
                }
                // No action of its own — a class is chosen by tapping it — but an empty bar still reserves
                // its 4 units and its 1-unit top margin, so the list's height is the finished screen's.
                LightBottomBar(items = emptyList())
            }
        }
    }
}
