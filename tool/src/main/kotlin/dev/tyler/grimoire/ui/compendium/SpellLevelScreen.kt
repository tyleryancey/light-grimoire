package dev.tyler.grimoire.ui.compendium

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIconConfiguration
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightLazyScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import dev.tyler.grimoire.compendium.CompendiumStore
import dev.tyler.grimoire.compendium.Kind
import dev.tyler.grimoire.ui.common.NavRow
import dev.tyler.grimoire.ui.common.ROW_HEIGHT_GRID_UNITS

/** The tap target on each side of the stepper's label — wide enough to hit without crowding the level. */
private const val ARROW_WIDTH_GRID_UNITS = 4f

/**
 * S13.2: the spells of one level, with the level itself on a stepper row (docs/UI-SPEC.md S13.2). The wheel's
 * turns are claimed by that stepper rather than by scrolling, so the row list is dragged by finger; the `◂`/`▸`
 * arrows call exactly what the wheel calls, since the emulator emits no wheel codes.
 *
 * The view model is built from [CompendiumStore.reader], which throws unless the import is Ready, so nothing
 * may navigate here before Home reports Ready.
 */
class SpellLevelScreen(
    sealedActivity: SealedLightActivity,
) : LightScreen<Unit, SpellLevelViewModel>(sealedActivity) {
    override val viewModelClass: Class<SpellLevelViewModel>
        get() = SpellLevelViewModel::class.java

    override fun createViewModel(): SpellLevelViewModel = SpellLevelViewModel(CompendiumStore.reader())

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()
        // Keyed on the view model alone — one per back-stack entry — and never on the level. A new
        // `LazyListState` per level would open each level at its own first spell, but it also swaps the object
        // `LightLazyScrollView` measures its scrollbar from, and that view remembers the state it was first
        // composed with under a `remember` with no keys (LightScrollView.kt): on a level step fast enough that
        // the `loading = true` frame never renders, the view is not rebuilt, so the scrollbar would keep
        // describing the level stepped away from while the rows scrolled underneath it. Identity stays fixed and
        // the level's fresh start is asked for explicitly instead.
        //
        // Not `rememberLazyListState()` either: the SDK composes every screen at one un-keyed call site, so a
        // pushed screen can land on the slots of the one it covers and inherit its scroll offset.
        val listState = remember(viewModel) { LazyListState() }
        LaunchedEffect(state.level) { listState.scrollToItem(0) }
        LightTheme(colors = colors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.TwoLineDetail("SPELLS", state.subtitle),
                )
                Stepper(state)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    // Nothing is drawn while a level loads: the stepper above already names the level being
                    // fetched, and a word in the list's place would flash on every turn of the wheel.
                    if (!state.loading) {
                        LightLazyScrollView(
                            listState = listState,
                            uniformItemHeightGridUnits = ROW_HEIGHT_GRID_UNITS,
                        ) {
                            items(state.spells.size) { index ->
                                val spell = state.spells[index]
                                NavRow(
                                    name = spell.name,
                                    detail = RefDetail.of(spell, DetailStyle.SCHOOL),
                                    onClick = { navigateTo({ ReaderScreen(it, Kind.SPELLS, spell.key, spell.name) }) },
                                )
                            }
                        }
                    }
                }
                // S13.2 has no action of its own; the empty bar still reserves the 4 units plus 1-unit margin
                // the wireframe's content height assumes.
                LightBottomBar(items = emptyList())
            }
        }
    }

    /** The wireframe's `◂ LEVEL n · count ▸` — one row of the list's own height, so the screen reads as a list. */
    @Composable
    private fun Stepper(state: SpellLevelUiState) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ROW_HEIGHT_GRID_UNITS.gridUnitsAsDp())
                .padding(horizontal = 1f.gridUnitsAsDp()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Arrow(LightIcons.BACK, available = state.hasLower) { viewModel.levelDown() }
            LightText(
                text = state.stepper,
                variant = LightTextVariant.Detail,
                align = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Arrow(LightIcons.ARROW_RIGHT, available = state.hasHigher) { viewModel.levelUp() }
        }
    }

    /**
     * One end of the stepper. At the end of the range (cantrips have no level below them, 9th none above) the
     * arrow is not drawn at all — the SDK's blank glyph holds the same width, so the label stays centred and
     * there is no dead tap target and no colour saying "disabled".
     */
    @Composable
    private fun Arrow(icon: LightIconConfiguration, available: Boolean, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .width(ARROW_WIDTH_GRID_UNITS.gridUnitsAsDp())
                .fillMaxHeight()
                .let { if (available) it.lightClickable { onClick() } else it },
            contentAlignment = Alignment.Center,
        ) {
            LightIcon(if (available) icon else LightIcons.SPACER)
        }
    }
}
