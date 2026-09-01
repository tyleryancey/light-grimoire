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
import dev.tyler.grimoire.compendium.CompendiumRef
import dev.tyler.grimoire.compendium.CompendiumStore
import dev.tyler.grimoire.compendium.Kind
import dev.tyler.grimoire.compendium.KindGroup
import dev.tyler.grimoire.ui.common.NavRow
import dev.tyler.grimoire.ui.common.QuietLine
import dev.tyler.grimoire.ui.common.ROW_HEIGHT_GRID_UNITS
import dev.tyler.grimoire.ui.common.SectionHeaderRow
import dev.tyler.grimoire.ui.common.WheelScrollEffect

/**
 * S13.1: the one generic group list, seeded with a hub row's [group] (docs/UI-SPEC.md S13.1). Sections and
 * rows come from [CompendiumListViewModel]; this screen only draws them and pushes a [ReaderScreen].
 *
 * Every row — a section header included — is exactly [ROW_HEIGHT_GRID_UNITS] tall, because
 * `LightLazyScrollView` computes its scrollbar and its drag position from that one number and nothing
 * enforces it; both `NavRow` and `SectionHeaderRow` fix their own height to it.
 *
 * The view model is built from [CompendiumStore.reader], which throws unless the import is Ready, so nothing
 * may navigate here before Home reports Ready. SPELLS is not one of this screen's groups — it has its own
 * S13.2 screen and the view model refuses it.
 */
class CompendiumListScreen(
    sealedActivity: SealedLightActivity,
    private val group: KindGroup,
) : LightScreen<Unit, CompendiumListViewModel>(sealedActivity) {
    override val viewModelClass: Class<CompendiumListViewModel>
        get() = CompendiumListViewModel::class.java

    override fun createViewModel(): CompendiumListViewModel = CompendiumListViewModel(CompendiumStore.reader(), group)

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
                    center = LightTopBarCenter.Text(state.title),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    if (state.loading) {
                        // The same quiet line the reader shows while it composes a record — the queries behind a
                        // group are indexed and quick, but the screen is pushed before any of them has run.
                        QuietLine("Opening…")
                    } else {
                        LightLazyScrollView(
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
                // No action on this screen — S13.1 keeps FIND on the hub — but an empty bar still reserves its
                // 4 units plus its 1-unit top margin, which is what the S13.1 wireframe's content height assumes.
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
}
