package dev.tyler.grimoire.ui.compendium

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import dev.tyler.grimoire.compendium.CompendiumStore
import dev.tyler.grimoire.compendium.KindGroup
import dev.tyler.grimoire.compendium.Search
import dev.tyler.grimoire.ui.common.NavRow
import dev.tyler.grimoire.ui.common.QuietLine
import dev.tyler.grimoire.ui.common.ROW_HEIGHT_GRID_UNITS
import dev.tyler.grimoire.ui.common.TextEditorScreen
import dev.tyler.grimoire.ui.common.WheelScrollEffect

/** The top bar's right action, and the title of the editor it opens — one string, drawn twice (S13, S13.3). */
private const val FIND = "FIND"

/**
 * S13: the compendium hub (docs/UI-SPEC.md S13). Nine rows into the bundle, and `FIND` across all of it.
 *
 * SPELLS is the one row with a screen of its own ([SpellLevelScreen], S13.2, whose wheel steps the level);
 * every other row pushes the one generic [CompendiumListScreen] seeded with its group.
 *
 * The view model is built from [CompendiumStore.reader], which throws unless the import is Ready, so nothing
 * may navigate here before Home reports Ready.
 */
class CompendiumHubScreen(
    sealedActivity: SealedLightActivity,
) : LightScreen<Unit, CompendiumHubViewModel>(sealedActivity) {
    override val viewModelClass: Class<CompendiumHubViewModel>
        get() = CompendiumHubViewModel::class.java

    override fun createViewModel(): CompendiumHubViewModel = CompendiumHubViewModel(CompendiumStore.reader())

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()
        // Not `rememberLazyListState()`: the SDK composes every screen at one un-keyed call site, so a pushed
        // screen can land on the slots of the one it covers and inherit its scroll offset. Keyed on the view
        // model — one per back-stack entry — this list opens at the top and keeps its own place.
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
                    center = LightTopBarCenter.Text("COMPENDIUM"),
                    rightButton = LightBarButton.Text(text = FIND, onClick = { find() }),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    if (state.loading) {
                        // The rows themselves are fixed, but their counts are not; drawing the nine names first
                        // and letting five of them grow a number a frame later would reflow the list under the
                        // finger, so the one quiet line covers the single COUNT query instead.
                        QuietLine("Opening…")
                    } else {
                        LightLazyScrollView(
                            listState = listState,
                            uniformItemHeightGridUnits = ROW_HEIGHT_GRID_UNITS,
                        ) {
                            items(state.rows.size) { index ->
                                val row = state.rows[index]
                                // No right-hand detail: S13 draws the count inside the name (`SPELLS (319)`),
                                // unlike S13.1 and S13.4, which keep that column for a school or a class.
                                NavRow(name = row.rowText, onClick = { open(row) })
                            }
                        }
                    }
                }
                // S13's only action is FIND, and it lives in the top bar; the empty bottom bar still reserves
                // its 4 units plus its 1-unit top margin, which the wireframe's content height assumes.
                LightBottomBar(items = emptyList())
            }
        }
    }

    /** SPELLS has its own level-stepping screen (S13.2); the other eight rows share the generic list (S13.1). */
    private fun open(row: HubRow) {
        if (row.group == KindGroup.SPELLS) {
            navigateTo({ SpellLevelScreen(it) })
        } else {
            navigateTo({ CompendiumListScreen(it, row.group) })
        }
    }

    /**
     * `FIND`: push the editor, and on a query push the results (S13.3 → S13.4).
     *
     * The editor pops *before* this callback runs (`LightActivity.goBack` delivers a result only after the
     * previous screen is back on top), so the two screens are never on the stack together and the branch stays
     * at its stated depth of 4: S0 → S13 → S13.4 → S10.
     *
     * A cancel — the drawn BACK or the hardware back button — delivers no result at all, so the callback simply
     * never runs and the hub is what the player is left looking at. A query too short to search is treated the
     * same way rather than pushing a screen of noise. The two halves of the search have different floors:
     * `Search.likePrefix` is null under two usable characters, so a one-letter query skips the
     * name query — the only ranked half — entirely, while `Search.ftsQuery` has no floor at all and
     * `CompendiumDao.textMatches` takes the first `Search.LIMIT` rows a `MATCH 'f*'` reaches in **rowid order,
     * ranked by nothing** (CompendiumDao.kt:169-174). Guarding on `likePrefix` holds the whole screen to the
     * floor the ranked half already enforces.
     *
     * The *typed* text is what the results screen is given, not `likePrefix`'s output: that lowercases and
     * strips the `LIKE` wildcards, and this string is also what re-seeds the editor on a re-FIND.
     */
    private fun find() {
        navigateTo({ TextEditorScreen(it, FIND) }) { typed ->
            val query = typed?.trim().orEmpty()
            if (Search.likePrefix(query) != null) navigateTo({ SearchResultsScreen(it, query) })
        }
    }
}
