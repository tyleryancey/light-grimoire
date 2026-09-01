package dev.tyler.grimoire.ui.compendium

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.verticalGridUnitsAsDp
import dev.tyler.grimoire.compendium.CompendiumRef
import dev.tyler.grimoire.compendium.CompendiumStore
import dev.tyler.grimoire.compendium.Kind
import dev.tyler.grimoire.ui.common.QuietLine
import dev.tyler.grimoire.ui.common.WheelScrollEffect

/** One wheel detent scrolls this many vertical grid units — about a sixth of the 31-unit screen. */
private const val WHEEL_STEP_UNITS = 5f

/**
 * S10 Reader: one compendium record, top bar and scrolling body (docs/UI-SPEC.md S10). A footer link
 * pushes another [ReaderScreen], which is the one static-depth exception (D5) — the chain is bounded by
 * how many links the reader actually taps, and BACK pops exactly one.
 *
 * The view model is built from [CompendiumStore.reader], which throws unless the import is Ready, so
 * nothing may navigate here before Home reports Ready.
 *
 * [name] is the row's own name, which every call site already holds: it is the top bar's title from the
 * first frame, so the bar is never blank while the record loads and still reads as itself on the
 * "not in the compendium" branch. [load][ReaderViewModel.load] replaces it with the record's own name.
 */
class ReaderScreen(
    sealedActivity: SealedLightActivity,
    private val kind: Kind,
    private val key: String,
    private val name: String,
) : LightScreen<Unit, ReaderViewModel>(sealedActivity) {
    override val viewModelClass: Class<ReaderViewModel>
        get() = ReaderViewModel::class.java

    override fun createViewModel(): ReaderViewModel = ReaderViewModel(CompendiumStore.reader(), kind, key, name)

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()
        // Not `rememberScrollState()`: the SDK composes every screen at one un-keyed call site
        // (LightActivity.onCreate), so a reader pushed from a reader lands on the parent's slots and would
        // inherit its scroll offset. Keyed on the view model, which is one per back-stack entry, each
        // reader in a chain opens at the top. Nothing is saved across process death because the SDK
        // rebuilds the back stack from the initial screen anyway.
        val scrollState = remember(viewModel) { ScrollState(0) }
        val stepPx = with(LocalDensity.current) { WHEEL_STEP_UNITS.verticalGridUnitsAsDp().toPx() }
        WheelScrollEffect(viewModel.ticks, scrollState) { stepPx }
        LightTheme(colors = colors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text(state.title.uppercase()),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    when {
                        state.loading -> QuietLine("Opening…")
                        state.missing -> QuietLine("Not in the compendium.")
                        else -> ReaderBody(
                            blocks = state.blocks,
                            links = state.links,
                            scrollState = scrollState,
                            onLink = ::openLink,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                // The UI-SPEC's S10 wireframe draws a bare bottom-bar row, the same empty-bar pattern as
                // S16 — distinct from S13.3, which draws no bar at all. It holds nothing until CAST arrives
                // with M3, but an empty LightBottomBar still reserves its 4-unit height and 1-unit top
                // margin, so the reading column is 23 vertical units, not the 28 it got with no bar at all.
                LightBottomBar(items = emptyList())
            }
        }
    }

    /**
     * Push a chained reader for a footer link. [CompendiumRef.kind] is the `records.kind` string;
     * `Kind.byId` throws on anything else, so the lookup is done leniently here and an unrecognized kind
     * is simply not navigable — a link row is never worth a crash.
     */
    private fun openLink(ref: CompendiumRef) {
        val target = Kind.entries.firstOrNull { it.id == ref.kind } ?: return
        navigateTo({ ReaderScreen(it, target, ref.key, ref.name) })
    }

}
