package dev.tyler.grimoire.ui.compendium

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.tyler.grimoire.compendium.CompendiumRef
import dev.tyler.grimoire.compendium.CompendiumStore
import dev.tyler.grimoire.compendium.Kind
import dev.tyler.grimoire.compendium.Search
import dev.tyler.grimoire.ui.common.NavRow
import dev.tyler.grimoire.ui.common.QuietLine
import dev.tyler.grimoire.ui.common.ROW_HEIGHT_GRID_UNITS
import dev.tyler.grimoire.ui.common.SectionHeaderRow
import dev.tyler.grimoire.ui.common.TextEditorScreen
import dev.tyler.grimoire.ui.common.WheelScrollEffect

/** The top bar's right action, and the title of the editor it re-opens — one string, drawn twice (S13.4, S13.3). */
private const val FIND = "FIND"

/**
 * S13.4: one query's results across every kind (docs/UI-SPEC.md S13.4) — the name matches under the same
 * non-tappable kind headers S13.1 uses, then the body matches under one `ALSO MENTIONED`. Bounded at
 * `Search.LIMIT` hits (the headers are extra rows), and re-queried in place from `FIND`.
 *
 * Every row — a section header included — is exactly [ROW_HEIGHT_GRID_UNITS] tall, because
 * `LightLazyScrollView` computes its scrollbar and its drag position from that one number and nothing enforces
 * it; both `NavRow` and `SectionHeaderRow` fix their own height to it.
 *
 * The view model is built from [CompendiumStore.reader], which throws unless the import is Ready, so nothing
 * may navigate here before Home reports Ready.
 */
class SearchResultsScreen(
    sealedActivity: SealedLightActivity,
    private val query: String,
) : LightScreen<Unit, SearchResultsViewModel>(sealedActivity) {
    override val viewModelClass: Class<SearchResultsViewModel>
        get() = SearchResultsViewModel::class.java

    override fun createViewModel(): SearchResultsViewModel =
        SearchResultsViewModel(CompendiumStore.reader(), query)

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()
        // Not `rememberLazyListState()`: the SDK composes every screen at one un-keyed call site, so a pushed
        // screen can land on the slots of the one it covers and inherit its scroll offset. Keyed on the view
        // model — one per back-stack entry — this list opens at the top and keeps its own place.
        val listState = remember(viewModel) { LazyListState() }
        // A re-FIND replaces the rows under a list that is still scrolled where the last query left it, so the
        // new results would open halfway down. Asked for explicitly rather than by keying the state on the
        // query: `LightLazyScrollView` remembers the `LazyListState` it was first composed with, so swapping
        // the object would leave its scrollbar describing the query that is gone (the same trap S13.2 documents).
        LaunchedEffect(state.query) { listState.scrollToItem(0) }
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
                    center = LightTopBarCenter.Text(state.title),
                    // Seeded with the query on screen, so a mistyped search is corrected rather than retyped.
                    rightButton = LightBarButton.Text(text = FIND, onClick = { find(state.query) }),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    when {
                        state.loading -> QuietLine("Searching…")
                        state.empty -> QuietLine("No matches.")
                        else -> LightLazyScrollView(
                            listState = listState,
                            uniformItemHeightGridUnits = ROW_HEIGHT_GRID_UNITS,
                        ) {
                            items(state.rows.size) { index ->
                                when (val row = state.rows[index]) {
                                    is ListRow.Header -> SectionHeaderRow(row.label)
                                    is ListRow.Entry -> NavRow(
                                        name = row.ref.name,
                                        detail = row.detail,
                                        onClick = { open(row.ref) },
                                    )
                                }
                            }
                        }
                    }
                }
                // S13.4's only action is FIND, and it lives in the top bar; the empty bottom bar still reserves
                // its 4 units plus its 1-unit top margin, which the wireframe's content height assumes.
                LightBottomBar(items = emptyList())
            }
        }
    }


    /**
     * Push the reader for a tapped row. [CompendiumRef.kind] is the `records.kind` string; `Kind.byId` throws
     * on anything else, so the lookup is lenient here and an unrecognized kind is simply not navigable.
     */
    private fun open(ref: CompendiumRef) {
        val target = Kind.entries.firstOrNull { it.id == ref.kind } ?: return
        navigateTo({ ReaderScreen(it, target, ref.key, ref.name) })
    }

    /**
     * `FIND` from the results themselves: the editor opens seeded with [seed] and pops back to *this* screen,
     * which re-queries in place — it never pushes a second results screen (S13.4). The stack reaches
     * S0 → S13 → S13.4 → S13.3 for as long as the editor is open, which is the deepest the compendium branch
     * goes outside a reader chain.
     *
     * A cancel delivers no result at all, so the callback never runs and the current results stay exactly as
     * they were; a query too short to search is treated the same way rather than replacing them with noise.
     * `Search.likePrefix` is null under two usable characters, which skips the ranked name query and leaves only
     * the FTS `MATCH`, whose rows the DAO takes in rowid order with no ranking — so the guard is the same floor
     * the ranked half already enforces, and S13's own `find` carries it too (the mechanism is spelled out there).
     *
     * The *typed* text is what the view model is given, not `likePrefix`'s output, which lowercases and strips
     * the `LIKE` wildcards; this string is what the next re-FIND re-seeds the editor with.
     */
    private fun find(seed: String) {
        navigateTo({ TextEditorScreen(it, FIND, seed) }) { typed ->
            val next = typed?.trim().orEmpty()
            if (Search.likePrefix(next) != null) viewModel.setQuery(next)
        }
    }
}
