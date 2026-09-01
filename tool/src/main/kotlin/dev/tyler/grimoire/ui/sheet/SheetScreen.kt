package dev.tyler.grimoire.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcon
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
import dev.tyler.grimoire.data.GrimoireStore
import dev.tyler.grimoire.ui.common.EmphasisText
import dev.tyler.grimoire.ui.common.Layout
import dev.tyler.grimoire.ui.common.NAV_ARROW_WIDTH_UNITS
import dev.tyler.grimoire.ui.common.NavRow
import dev.tyler.grimoire.ui.common.PipStrip
import dev.tyler.grimoire.ui.common.QuietLine
import dev.tyler.grimoire.ui.common.ROW_HEIGHT_GRID_UNITS
import dev.tyler.grimoire.ui.common.ROW_SIDE_MARGIN_UNITS
import dev.tyler.grimoire.ui.common.SHEET_HEADER_PAD_UNITS
import dev.tyler.grimoire.ui.common.SHEET_STAR_ICON_UNITS
import dev.tyler.grimoire.ui.common.SHEET_STAR_TAP_WIDTH_UNITS
import dev.tyler.grimoire.ui.common.SlotStripModel
import dev.tyler.grimoire.ui.common.StatHeaderLine
import dev.tyler.grimoire.ui.common.WheelScrollEffect
import dev.tyler.grimoire.ui.hp.HpScreen

/** The gap between the `HP` label and its numbers, and between two bands of the slot strip. */
private const val SHEET_INLINE_GAP_UNITS = 1f

/**
 * S1 Sheet hub: one character's mutable half, nine rows deep (docs/UI-SPEC.md S1).
 *
 * **A 4-unit pinned header over a uniform list, and neither may grow.** Nine rows at the tool's one
 * [ROW_HEIGHT_GRID_UNITS] height are 22.5 of the 23 content units on their own, so the header gets 4 —
 * half a unit of pad, two `Detail` lines, half a unit of pad — and is drawn once, outside the scroll. Two
 * `Copy` lines would be 4.65 and the screen would need 27.15 units of 23. The list beneath is a
 * `LightLazyScrollView`, whose scrollbar and drag maths need every row to be exactly one height: the HP
 * row, the slot row and the seven text rows are all [ROW_HEIGHT_GRID_UNITS], and nothing here draws a
 * taller one. 19 units are left, so 7.6 rows are visible — `HP` through `REST`, which is every row the
 * one-tap contract names — and the last two arrive on one wheel detent.
 *
 * **Sideways, the list is the tool's narrowest: 21 columns, not 25.** The rows always overflow the 19-unit
 * viewport — 22.5 units of them, or 20 for a non-caster who loses the slot row — so the scrollbar is always
 * drawn, and `LightLazyScrollView` then spends its 2-unit `Outside` gutter twice: once for the bar's place
 * in the `Row`, once for the pad on the weighted `LazyColumn`. The row margins take two more
 * ([ROW_SIDE_MARGIN_UNITS] derives all three cases). One row is near the edge:
 * `FEATURES & RESOURCES` is 20 characters plus the reserved arrow's 2,
 * i.e. 22 against 21 by the UI-SPEC's deliberately conservative one-character-per-unit conversion at `Copy`.
 * `NavRow` ellipsizes rather than clips, so the worst case is a lost letter on a row below the fold —
 * **one screenshot of S1 scrolled to the bottom settles it**, and the fix if it does clip is the label's
 * wording, not the layout. The pinned header is unaffected: drawn outside the scroll view, it keeps all 25.
 *
 * **M3 task 1 interim: only `HP` navigates.** S3 is the one destination that exists; `TURN`, `CHECKS &
 * SAVES`, `SPELLS`, `CONDITIONS`, `REST`, `FEATURES & RESOURCES`, `GEAR & COIN` and the slot row are drawn
 * with **no arrow and no tap target** — a read-out, not a disabled control, exactly as `NavRow` documents
 * and as S9's `□ Holy Symbol` and S3's STABLE state already do. They are drawn rather than omitted (S0's
 * M2 interim dropped its rows) because the whole S1 section is an argument about whether nine rows fit
 * under a four-unit header: a hub that showed one row would leave that untested until the last screen
 * landed, and two of the inert rows — the slot strip and the conditions line — carry real state a player
 * reads whether or not it opens anything. `EDIT` (S12) and the bottom bar's `DICE` (S15) are the same
 * interim: no dead controls, so the top bar's right button and the bottom bar's item are simply absent —
 * an empty `LightBottomBar` still reserves its 4 units and its 1-unit top margin, so the budget above is
 * the finished screen's. **This deviates from the S1 wireframe and wants ratification**; the spec carries
 * no S1 interim note today, unlike S0's and S3's.
 *
 * [name] is the character's name as the row that pushed this screen already holds it: it is the top bar's
 * title from the first frame, so the bar is never blank through the load — which here means the character,
 * the compendium's armor table and the whole derivation, the longest wait in the tool — and still reads as
 * itself on the "No such character." branch. [SheetViewModel] replaces it with the stored name.
 *
 * **The view model is built from [CompendiumStore.reader], which throws unless the import is Ready**, and
 * `createViewModel` runs inside `navigateTo` — so nothing may push this screen before Home reports Ready.
 * S1 is reached from S0's Ready branch, and Ready is sticky, so a row that can be tapped at all is safe;
 * nothing in `ui/sheet/` enforces that on its own (the same contract `CompendiumListScreen` states).
 */
class SheetScreen(
    sealedActivity: SealedLightActivity,
    private val characterId: String,
    private val name: String,
) : LightScreen<Unit, SheetViewModel>(sealedActivity) {
    override val viewModelClass: Class<SheetViewModel>
        get() = SheetViewModel::class.java

    override fun createViewModel(): SheetViewModel = SheetViewModel(
        characterId = characterId,
        repo = GrimoireStore.characters(lightContext),
        reader = CompendiumStore.reader(),
        name = name,
    )

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()
        // Not `rememberLazyListState()`: the SDK composes every screen at one un-keyed call site, so a pushed
        // screen can land on the slots of the one it covers and inherit its scroll offset. Keyed on the view
        // model — one per back-stack entry — this list opens at the top and keeps its own place.
        val listState = remember(viewModel) { LazyListState() }
        val rowHeightPx = with(LocalDensity.current) { ROW_HEIGHT_GRID_UNITS.gridUnitsAsDp().toPx() }
        // Three rows per detent, `WheelScrollEffect`'s default and the figure S1 states: 7.6 of the nine rows
        // are visible, so one turn is what brings the last two up.
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
                    when {
                        state.loading -> QuietLine("Opening…")
                        // BACK is drawn above whatever this branch says, so a stale id is always escapable.
                        state.missing -> QuietLine("No such character.")
                        else -> SheetBody(
                            state = state,
                            listState = listState,
                            onInspiration = viewModel::toggleInspiration,
                            onRow = ::open,
                        )
                    }
                }
                // Empty rather than the spec's `DICE`: S15 does not exist yet — see the class KDoc's interim.
                LightBottomBar(items = emptyList())
            }
        }
    }

    /**
     * Push the screen a tapped row leads to. Only [SheetDestination.HP] has one in M3 task 1; every other
     * row is drawn inert and never reaches here, and the `when` lists them so the screen that adds S2 or S5
     * cannot forget to route it.
     */
    private fun open(destination: SheetDestination) {
        when (destination) {
            SheetDestination.HP -> navigateTo({ HpScreen(it, characterId) })
            SheetDestination.SLOTS,
            SheetDestination.TURN,
            SheetDestination.CHECKS,
            SheetDestination.SPELLS,
            SheetDestination.CONDITIONS,
            SheetDestination.REST,
            SheetDestination.FEATURES,
            SheetDestination.GEAR,
            -> Unit
        }
    }
}

/** Which rows are live in this build — the whole of the M3 task 1 interim, in one place. */
private fun navigates(destination: SheetDestination): Boolean = destination == SheetDestination.HP

@Composable
private fun SheetBody(
    state: SheetUiState,
    listState: LazyListState,
    onInspiration: () -> Unit,
    onRow: (SheetDestination) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SheetHeader(state = state, onInspiration = onInspiration)
        LightLazyScrollView(
            // The header is drawn at its natural height and the list takes whatever is left, which is what
            // makes the 4-unit header a budget rather than a clamp: a line box that rounds up costs the
            // list a unit of a row it can scroll, never the stat line a character of it cannot.
            modifier = Modifier.weight(1f),
            listState = listState,
            uniformItemHeightGridUnits = ROW_HEIGHT_GRID_UNITS,
        ) {
            items(state.rows.size) { index ->
                val row = state.rows[index]
                val onClick = if (navigates(row.destination)) ({ onRow(row.destination) }) else null
                when (row) {
                    is SheetRow.Hp -> HpRow(row = row, onClick = onClick)
                    is SheetRow.Slots -> SlotRow(strip = row.strip, label = row.label, onClick = onClick)
                    is SheetRow.Nav -> NavRow(name = row.label, detail = row.detail, onClick = onClick)
                }
            }
        }
    }
}

/**
 * The pinned header: identity line with the star, then the stat line, half a unit of pad above and below.
 *
 * **The 4 units are a budget, not a clamp.** No height is set here — the two `Detail` line boxes are 1.50
 * units each by the UI-SPEC's table, so 0.5 + 1.5 + 1.5 + 0.5 is exactly 4.0 — because a device that rounds
 * a line box up should take a unit from the list below (which scrolls) rather than clip the stat line
 * (which cannot).
 */
@Composable
private fun SheetHeader(state: SheetUiState, onInspiration: () -> Unit) {
    Spacer(Modifier.height(SHEET_HEADER_PAD_UNITS.gridUnitsAsDp()))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // `StatHeaderLine` carries the row's side margins, so the star is placed against its own end pad.
        StatHeaderLine(text = state.identity, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .padding(end = ROW_SIDE_MARGIN_UNITS.gridUnitsAsDp())
                .width(SHEET_STAR_TAP_WIDTH_UNITS.gridUnitsAsDp())
                // The height is stated rather than left to the glyph, so the target really is the line's own
                // box (the figure `SHEET_STAR_TAP_WIDTH_UNITS` is documented against) and not the 1.3 units
                // of star inside it. It cannot grow the header: the line beside it is exactly this tall.
                .height(Layout.DETAIL_LINE_UNITS.gridUnitsAsDp())
                .lightClickable { onInspiration() },
            contentAlignment = Alignment.Center,
        ) {
            // Filled when held, hollow when not — 2014 inspiration, the one thing this screen itself changes.
            LightIcon(
                icon = if (state.inspiration) LightIcons.STAR else LightIcons.STAR_OUTLINE,
                size = SHEET_STAR_ICON_UNITS,
            )
        }
    }
    StatHeaderLine(text = state.stats)
    Spacer(Modifier.height(SHEET_HEADER_PAD_UNITS.gridUnitsAsDp()))
}

/**
 * `HP  31 / 43   TEMP 0  ▸` — the numbers in `Copy`, **bold** when bloodied, and the temp column lightened
 * on the right where every other row's detail sits.
 *
 * The bold is `EmphasisText`, not a heavier variant: `Subheading` and `Copy` are both 30 sp
 * `FontWeight.Normal`, so a swap would change the line box and not the weight (see `Emphasis.kt`).
 */
@Composable
private fun HpRow(row: SheetRow.Hp, onClick: (() -> Unit)?) {
    SheetRowFrame(onClick = onClick) {
        LightText(text = row.label, variant = LightTextVariant.Copy, maxLines = 1)
        Spacer(Modifier.width(SHEET_INLINE_GAP_UNITS.gridUnitsAsDp()))
        EmphasisText(
            text = row.numbers,
            variant = LightTextVariant.Copy,
            bold = row.bloodied,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        LightText(text = row.suffix, variant = LightTextVariant.Detail, lighten = true, maxLines = 1)
    }
}

/**
 * `●●●● ●●● ●○   slots  ▸` — one [PipStrip] per band, filled = still castable, with the row's word in the
 * right-hand column instead of the left. The pips are the content here; `slots` only says what they are.
 *
 * Display pips, so no `onTap`: spending a slot is S5's job, and a strip that could be tapped here would be
 * two different controls wearing one mark.
 */
@Composable
private fun SlotRow(strip: SlotStripModel, label: String, onClick: (() -> Unit)?) {
    SheetRowFrame(onClick = onClick) {
        strip.groups.forEachIndexed { index, group ->
            if (index > 0) Spacer(Modifier.width(SHEET_INLINE_GAP_UNITS.gridUnitsAsDp()))
            PipStrip(value = group.available, total = group.total)
        }
        Spacer(Modifier.weight(1f))
        LightText(text = label, variant = LightTextVariant.Detail, lighten = true, maxLines = 1)
    }
}

/**
 * The frame the HP and slot rows share with `NavRow`: one [ROW_HEIGHT_GRID_UNITS] of height, the row's side
 * margins, the tap target when there is somewhere to go, and the trailing arrow — or the width the arrow
 * would have taken, so the right column keeps one edge down a list of live and inert rows.
 */
@Composable
private fun SheetRowFrame(onClick: (() -> Unit)?, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT_GRID_UNITS.gridUnitsAsDp())
            .then(if (onClick != null) Modifier.lightClickable { onClick() } else Modifier)
            .padding(horizontal = ROW_SIDE_MARGIN_UNITS.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
        if (onClick != null) {
            LightIcon(LightIcons.ARROW_RIGHT)
        } else {
            Spacer(Modifier.width(NAV_ARROW_WIDTH_UNITS.gridUnitsAsDp()))
        }
    }
}
