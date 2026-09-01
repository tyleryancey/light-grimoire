package dev.tyler.grimoire.ui.hp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollBarPosition
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import dev.tyler.grimoire.data.GrimoireStore
import dev.tyler.grimoire.rules.Mulberry32
import dev.tyler.grimoire.ui.common.ChipRow
import dev.tyler.grimoire.ui.common.EmphasisText
import dev.tyler.grimoire.ui.common.HP_BLOCK_GAP_UNITS
import dev.tyler.grimoire.ui.common.HP_TIGHT_GAP_UNITS
import dev.tyler.grimoire.ui.common.Layout
import dev.tyler.grimoire.ui.common.NumberPad
import dev.tyler.grimoire.ui.common.OutlineButton
import dev.tyler.grimoire.ui.common.PAD_BUTTON_HEIGHT_UNITS
import dev.tyler.grimoire.ui.common.PipStrip
import dev.tyler.grimoire.ui.common.QuietLine
import dev.tyler.grimoire.ui.common.ROW_HEIGHT_GRID_UNITS
import dev.tyler.grimoire.ui.common.ROW_SIDE_MARGIN_UNITS

/** How wide `[ REVIVE ]` is drawn — narrower than a pad row, so a deliberate action does not look like one. */
private const val REVIVE_WIDTH_FRACTION = 0.6f

/**
 * S3 HP pad: the mutable half of the paper sheet's hit-point block, in four states (docs/UI-SPEC.md S3).
 *
 * **The bars never change.** BACK, `HP`, `UNDO` and the bottom bar are the same in all four states; only
 * the middle of the screen does anything different. `UNDO` in particular is drawn even when there is
 * nothing to undo — the spec keeps the chrome fixed, and a tap with an empty snapshot is a consumed
 * no-op, which is a cheaper surprise than a button that appears and disappears under the player's thumb.
 *
 * **The death panel is inserted above the pad, never swapped in for it.** `HEAL +1` is the only way a
 * downed character gets back up, and a panel that took the pad away would remove that control at exactly
 * the moment it matters. DEAD is the one state that drops the pad, because `Ledger.heal`, `longRest`,
 * `deathSave` and `spendHitDie` all return a dead character unchanged: every button there would be dead.
 *
 * The body scrolls ([LightScrollBarPosition.Inside] costs no content width and draws nothing until the
 * content overflows) because DYING spends 22.4 of the 23 content units — see `HP_BLOCK_GAP_UNITS` for the
 * arithmetic. Touch drag is the only thing that scrolls it: the wheel's turns are claimed by the verb.
 */
class HpScreen(
    sealedActivity: SealedLightActivity,
    private val characterId: String,
) : LightScreen<Unit, HpViewModel>(sealedActivity) {
    override val viewModelClass: Class<HpViewModel>
        get() = HpViewModel::class.java

    /**
     * One [Mulberry32] per visit, seeded from the clock and advanced by every roll, so successive death
     * saves come from one stream rather than from a fresh seed each time. M5's Dice screen will own a
     * shared roller; until it exists, S3's `[ ROLL DEATH SAVE ]` needs a d20 and this is the whole of it.
     */
    override fun createViewModel(): HpViewModel {
        val rng = Mulberry32(System.nanoTime().toInt())
        return HpViewModel(
            characterId = characterId,
            repo = GrimoireStore.characters(lightContext),
            roll = { rng.die(20) },
        )
    }

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val state by viewModel.state.collectAsState()
        LightTheme(colors = colors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text("HP"),
                    rightButton = LightBarButton.Text(text = "UNDO", onClick = { viewModel.undo() }),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    when {
                        state.loading -> QuietLine("Opening…")
                        state.missing -> QuietLine("No such character.")
                        else -> HpBody(
                            state = state,
                            onDelta = viewModel::pad,
                            onVerb = viewModel::select,
                            onRoll = viewModel::rollDeathSave,
                            onSuccess = viewModel::tapSuccess,
                            onFailure = viewModel::tapFailure,
                            onRevive = viewModel::revive,
                        )
                    }
                }
                // Empty rather than the spec's `REST`: S8 does not exist yet, and the tool's rule is no dead
                // controls (S0's M2 interim followed it, S3 STABLE drops its roll button for it). An empty
                // LightBottomBar still reserves its 4-unit height and 1-unit top margin, so the content
                // budget every measurement above is stated against is the same one the finished screen has.
                LightBottomBar(items = emptyList())
            }
        }
    }
}

@Composable
private fun HpBody(
    state: HpUiState,
    onDelta: (Int) -> Unit,
    onVerb: (Verb) -> Unit,
    onRoll: () -> Unit,
    onSuccess: (Int) -> Unit,
    onFailure: (Int) -> Unit,
    onRevive: () -> Unit,
) {
    LightScrollView(
        modifier = Modifier.fillMaxSize(),
        scrollBarPosition = LightScrollBarPosition.Inside,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ROW_SIDE_MARGIN_UNITS.gridUnitsAsDp()),
        ) {
            Gap(HP_TIGHT_GAP_UNITS)
            StatusBlock(state)
            Gap(HP_BLOCK_GAP_UNITS)
            if (state.mode == HpMode.DEAD) {
                OutlineButton(
                    label = "REVIVE",
                    onClick = onRevive,
                    modifier = Modifier
                        .fillMaxWidth(REVIVE_WIDTH_FRACTION)
                        .align(Alignment.CenterHorizontally),
                )
                Gap(HP_TIGHT_GAP_UNITS)
                LastActionLine(state.lastAction)
            } else {
                if (state.mode == HpMode.DYING || state.mode == HpMode.STABLE) {
                    DeathPanel(state = state, onRoll = onRoll, onSuccess = onSuccess, onFailure = onFailure)
                    Gap(HP_BLOCK_GAP_UNITS)
                }
                NumberPad(onDelta = onDelta)
                Gap(HP_TIGHT_GAP_UNITS)
                LastActionLine(state.lastAction)
                Gap(HP_TIGHT_GAP_UNITS)
                ChipRow(
                    labels = Verb.entries.map { it.label },
                    selected = state.verb.ordinal,
                    onSelect = { onVerb(Verb.entries[it]) },
                )
            }
        }
    }
}

/**
 * The numbers, and whatever the state says about them: `31 / 43` over `temp 0` in UP, `0 / 43   DOWN` in
 * DYING and STABLE — `0 / 43 · temp 6   DOWN` when there are temporary hit points to trail — and `DEAD`
 * over `0 / 43` in DEAD.
 *
 * **The order on that line is temp, then the gap, then the badge**, in every state that draws one: the
 * badge is the last thing on the line in all three of the spec's frames, and the badge-first form the
 * screen used to draw contradicted its own `*(b)*` paragraph. Nothing in the JVM gate can see this order —
 * `HpUiStateTest` pins the two strings, not which is drawn first — so it is checked on the device.
 *
 * `DEAD` is drawn in `Heading` — 38 sp against `Copy`/`Subheading`'s shared 30 — which is genuinely larger
 * rather than merely different, so unlike S1's bloodied HP it needs no weight to carry itself. The numbers
 * take the same `Heading`, bold when bloodied, through the tool's one bold-capable helper.
 */
@Composable
private fun StatusBlock(state: HpUiState) {
    if (state.mode == HpMode.DEAD) {
        CenteredRow {
            EmphasisText(text = state.badge, variant = LightTextVariant.Heading, maxLines = 1)
        }
    }
    CenteredRow {
        EmphasisText(
            text = state.numbers,
            variant = LightTextVariant.Heading,
            bold = state.bloodied,
            maxLines = 1,
        )
        // Temp first and adjacent — it carries its own ` · ` — then the gap, then the badge, which is the
        // last thing on the line in every frame the spec draws.
        val temp = state.statusTemp
        if (temp.isNotEmpty()) {
            LightText(
                text = temp,
                variant = LightTextVariant.Detail,
                lighten = true,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val trailing = state.statusTrailing
        if (trailing.isNotEmpty()) {
            Spacer(Modifier.width(HP_BLOCK_GAP_UNITS.gridUnitsAsDp()))
            LightText(
                text = trailing,
                variant = LightTextVariant.Detail,
                lighten = true,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (state.mode == HpMode.UP) {
        CenteredRow {
            LightText(text = "temp ${state.temp}", variant = LightTextVariant.Detail, lighten = true, maxLines = 1)
        }
    }
}

/**
 * The death-save panel: two labelled pip strips and, in DYING, the roll button.
 *
 * The pips are tappable in DYING and display-only in STABLE — `PipStrip` takes no `onTap` there, so they
 * are a read-out rather than six controls that would do nothing (`Ledger.deathSave` returns a stable
 * character unchanged). **Both states pass `tapSized = true`**, so dropping the handler drops only the
 * handler: the spec draws STABLE's `success ○ ○ ○  failure ○○○` identical to DYING's, and the default
 * `tapSized = onTap != null` would shrink each strip from 5.1 units to 2.0 exactly as the third success
 * landed. STABLE's sentence is likewise drawn in a box the exact height of the roll button it replaces, so
 * nothing above or below it moves between the two states.
 */
@Composable
private fun DeathPanel(
    state: HpUiState,
    onRoll: () -> Unit,
    onSuccess: (Int) -> Unit,
    onFailure: (Int) -> Unit,
) {
    val tappable = state.mode == HpMode.DYING
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT_GRID_UNITS.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(text = "success", variant = LightTextVariant.Detail, lighten = true, maxLines = 1)
        Spacer(Modifier.width(HP_TIGHT_GAP_UNITS.gridUnitsAsDp()))
        PipStrip(
            value = state.successes,
            total = DEATH_SAVE_PIPS,
            onTap = if (tappable) onSuccess else null,
            tapSized = true,
        )
        Spacer(Modifier.weight(1f))
        LightText(text = "failure", variant = LightTextVariant.Detail, lighten = true, maxLines = 1)
        Spacer(Modifier.width(HP_TIGHT_GAP_UNITS.gridUnitsAsDp()))
        PipStrip(
            value = state.failures,
            total = DEATH_SAVE_PIPS,
            onTap = if (tappable) onFailure else null,
            tapSized = true,
        )
    }
    Gap(HP_TIGHT_GAP_UNITS)
    if (tappable) {
        OutlineButton(label = "ROLL DEATH SAVE", onClick = onRoll, modifier = Modifier.fillMaxWidth())
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PAD_BUTTON_HEIGHT_UNITS.gridUnitsAsDp()),
            contentAlignment = Alignment.Center,
        ) {
            LightText(
                text = "Stable. No further saves.",
                variant = LightTextVariant.Detail,
                lighten = true,
                maxLines = 1,
            )
        }
    }
}

/**
 * The one quiet line under the pad. Its box is reserved whether or not there is anything to say, so the
 * verb chips do not move up the screen the first time the player touches a button.
 */
@Composable
private fun LastActionLine(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(Layout.DETAIL_LINE_UNITS.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        if (text.isNotEmpty()) {
            LightText(
                text = text,
                variant = LightTextVariant.Detail,
                lighten = true,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CenteredRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun Gap(units: Float) {
    Spacer(Modifier.height(units.gridUnitsAsDp()))
}
