package dev.tyler.grimoire.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlin.math.abs

/** The pad the UI-SPEC draws by default: `−10 −5 −1` over `+10 +5 +1`. */
val DEFAULT_PAD_DELTAS = listOf(10, 5, 1)

/**
 * The wireframes' `[ ROLL ]` bracket convention, made real: a stroked rectangle with a centred
 * `Button` label. S3's `±n` pad and `[ ROLL DEATH SAVE ]` / `[ REVIVE ]`, S8's `[ROLL]` and
 * `[AVG]`, S15's roll button.
 *
 * `sdk:ui` has no button component outside the bars, and the bars hold screen actions only, so a
 * control in the body of a screen has to be drawn. It is drawn rather than lettered: writing the
 * brackets as text would put them in the type's own advance widths and they would not line up
 * across two buttons of different label lengths.
 *
 * [enabled] draws the button inert — stroke and label in `contentSecondary`, no click. S8 needs it
 * for `[ROLL]`/`[AVG]` whenever `deathSaves.dead`, where `Ledger.spendHitDie` now returns the
 * character unchanged: the guard is what makes the engine safe, this is what keeps the screen from
 * drawing a live-looking control that would do nothing.
 */
@Composable
fun OutlineButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    heightUnits: Float = PAD_BUTTON_HEIGHT_UNITS,
    enabled: Boolean = true,
) {
    val colors = LightThemeTokens.colors
    val ink = if (enabled) colors.content else colors.contentSecondary
    val density = LocalDensity.current
    val strokePx = with(density) { PIP_STROKE_UNITS.gridUnitsAsDp().toPx() }
    val cornerPx = with(density) { PAD_BUTTON_CORNER_UNITS.gridUnitsAsDp().toPx() }
    Box(
        modifier = modifier
            .height(heightUnits.gridUnitsAsDp())
            .lightClickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            // Inset by half the stroke so the border sits inside the button's bounds rather than
            // straddling them — two buttons side by side keep their full gap.
            drawRoundRect(
                color = ink,
                topLeft = Offset(strokePx / 2f, strokePx / 2f),
                size = Size(size.width - strokePx, size.height - strokePx),
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                style = Stroke(width = strokePx),
            )
        }
        LightText(
            text = label,
            variant = LightTextVariant.Button,
            lighten = !enabled,
            maxLines = 1,
        )
    }
}

/**
 * S3's signed number pad: `−10 −5 −1` over `+10 +5 +1`, two rows of equal-weight [OutlineButton]s.
 *
 * The pad is signed rather than paired with a verb toggle because each verb chip is itself signed
 * (S3's verb table): the same six buttons cover both directions of every correction, and the
 * caller — not this component — decides which `Event` a sign reaches. `onDelta` is handed the
 * signed amount, so `−10` arrives as `-10`.
 *
 * [deltas] are magnitudes; [padMagnitudes] normalises them, so a caller cannot produce a lopsided
 * pad by passing a negative or a duplicate.
 */
@Composable
fun NumberPad(
    deltas: List<Int> = DEFAULT_PAD_DELTAS,
    onDelta: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val magnitudes = padMagnitudes(deltas)
    Column(modifier = modifier.fillMaxWidth()) {
        PadRow(magnitudes.map { -it }, onDelta)
        Spacer(Modifier.height(PAD_ROW_GAP_UNITS.gridUnitsAsDp()))
        PadRow(magnitudes, onDelta)
    }
}

/**
 * The magnitudes a [NumberPad] draws, given whatever the caller passed: absolute values, zeroes
 * dropped, duplicates dropped, order kept.
 *
 * Pure so the JVM gate can hold the pad to its shape — the two rows must be mirror images, which is
 * only true if every entry is a positive magnitude. A zero button would be a control that does
 * nothing, and a repeated one would give the pad two identical keys.
 */
fun padMagnitudes(deltas: List<Int>): List<Int> =
    deltas.map { abs(it) }.filter { it > 0 }.distinct()

/** One row of the pad: equal-weight buttons, labelled by the tool's one signed-number formatter. */
@Composable
private fun PadRow(values: List<Int>, onDelta: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PAD_BUTTON_GAP_UNITS.gridUnitsAsDp()),
    ) {
        for (value in values) {
            OutlineButton(
                label = Signs.mod(value),
                onClick = { onDelta(value) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
