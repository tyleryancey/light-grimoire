package dev.tyler.grimoire.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlin.math.max
import kotlin.math.min

/** What a run of pips means, and so which of them are filled — the whole of [pipStates]. */
enum class PipStyle {
    /**
     * A quantity out of a maximum: the first `value` pips are filled. Slots, counter uses, hit
     * dice, death saves — every `●●●○` in the wireframes.
     */
    FILL,

    /**
     * A position on a scale: exactly the pip at index `value` is filled. S7's exhaustion indicator,
     * whose `○●○○○○` at level 1 marks *where* the character is, not how much of something is left.
     */
    LEVEL,
}

/** The two mark shapes the tool draws: a pip and a checkbox. */
enum class MarkShape {
    /** `●` / `○` — a pip. Slots, uses, death saves, exhaustion. */
    CIRCLE,

    /** `■` / `□` — a box. S9's equipped column. */
    SQUARE,
}

/**
 * Which pips of a strip are filled, as a pure function the JVM gate can pin.
 *
 * Out-of-range input draws nothing rather than throwing: a [total] below zero is an empty strip,
 * and a [value] past either end of a [PipStyle.LEVEL] scale simply lights no pip. A strip is a
 * read-out of state the player owns, and a mis-transcribed number is not worth a crash on the one
 * screen they are looking at.
 */
fun pipStates(value: Int, total: Int, style: PipStyle): List<Boolean> =
    List(max(0, total)) { i ->
        when (style) {
            PipStyle.FILL -> i < value
            PipStyle.LEVEL -> i == value
        }
    }

/**
 * One mark, filled or hollow, drawn by hand.
 *
 * **Both states are drawn here rather than taken from `LightIcons`.** The SDK's ~106 glyphs hold no
 * half-filled or hollow circle, and the nearest pair (`STAR`/`STAR_OUTLINE`, `SELECT_ON`/`SELECT_OFF`)
 * are two separate drawables whose ink differs — a strip built from them would visibly change
 * diameter or weight as a pip is spent, which on a monochrome screen reads as a different mark
 * rather than the same mark in a different state. Drawn as a disc and as a ring of the same
 * [sizeUnits] *outer* diameter, the only thing that changes is whether the middle is filled: the
 * ring is stroked at radius `(edge − stroke) / 2`, so its outside edge lands exactly where the
 * disc's does.
 *
 * The colour is read from `LightThemeTokens.colors.content` in the composable and handed to the
 * draw scope — a `DrawScope` is not a composable and can reach no theme of its own, and the tool
 * writes no colour literals.
 */
@Composable
fun Mark(
    filled: Boolean,
    shape: MarkShape = MarkShape.CIRCLE,
    sizeUnits: Float = PIP_DIAMETER_UNITS,
    strokeUnits: Float = PIP_STROKE_UNITS,
    modifier: Modifier = Modifier,
) {
    val ink = LightThemeTokens.colors.content
    val strokePx = with(LocalDensity.current) { strokeUnits.gridUnitsAsDp().toPx() }
    Canvas(modifier = modifier.size(sizeUnits.gridUnitsAsDp())) {
        val edge = min(size.width, size.height)
        // Never a stroke wider than the mark: a mis-set weight should thicken the ring, not invert it.
        val weight = strokePx.coerceIn(0f, edge / 2f)
        when (shape) {
            MarkShape.CIRCLE ->
                if (filled) {
                    drawCircle(color = ink, radius = edge / 2f)
                } else {
                    drawCircle(color = ink, radius = (edge - weight) / 2f, style = Stroke(width = weight))
                }

            MarkShape.SQUARE ->
                if (filled) {
                    drawRect(color = ink, size = Size(edge, edge))
                } else {
                    drawRect(
                        color = ink,
                        topLeft = Offset(weight / 2f, weight / 2f),
                        size = Size(edge - weight, edge - weight),
                        style = Stroke(width = weight),
                    )
                }
        }
    }
}

/**
 * A run of pips — the tool's one read-out for "how much of this is left" (S1's slot row, S2's
 * per-row slot pips, S5's slot line, S6's counters, S7's exhaustion, S8's hit dice) and, when
 * [onTap] is given, for recording one (S3's death saves, S5's spend-a-slot).
 *
 * **The component is deliberately dumb: it knows nothing about what a tap means.** S5 taps a
 * *filled* pip to spend a slot, S3 taps an *empty* one to record a save, and S1 taps none at all;
 * each caller reads the index and decides. Passing `null` for [onTap] is what makes a strip
 * display-only, which is exactly what S3 STABLE needs — six hollow pips that cannot be tapped,
 * because `Ledger.deathSave` returns the character unchanged once stable and a live-looking pip
 * there would be the dead control that state drops its roll button to avoid.
 *
 * A tap-sized strip is drawn at [PIP_TAP_DIAMETER_UNITS] on a [PIP_TAP_PITCH_UNITS] pitch inside a
 * full row-height tap box (≈ 26 × 38 dp); a display strip is drawn at [PIP_DIAMETER_UNITS] on the
 * [PIP_PITCH_UNITS] pitch of one `Detail` character, so it sits inline with the text beside it.
 * That is why a tap-sized run reads spaced (`○ ○ ○`) where a display run reads tight (`●●●●`) — the
 * wireframes draw both, and 10 dp of pip would not be tappable at the size the frames imply.
 *
 * **[tapSized] is separate from [onTap] so a strip can stop responding without changing shape.** It
 * defaults to `onTap != null`, which is right everywhere a strip is either a control or a glyph — but
 * S3 STABLE is neither: it drops the handler and keeps `tapSized = true`, because the spec draws its
 * `success ○ ○ ○  failure ○○○` identical to DYING's and promises the chrome does not move between the
 * two states. Tying the two together would shrink each strip from 5.1 units to 2.0 the instant the
 * third success landed, which is the six pips visibly jumping at the moment the player is reading them.
 */
@Composable
fun PipStrip(
    value: Int,
    total: Int,
    style: PipStyle = PipStyle.FILL,
    onTap: ((Int) -> Unit)? = null,
    tapSized: Boolean = onTap != null,
    modifier: Modifier = Modifier,
) {
    val tap = onTap
    val pitch = if (tapSized) PIP_TAP_PITCH_UNITS else PIP_PITCH_UNITS
    val diameter = if (tapSized) PIP_TAP_DIAMETER_UNITS else PIP_DIAMETER_UNITS
    val stroke = if (tapSized) PIP_TAP_STROKE_UNITS else PIP_STROKE_UNITS
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        pipStates(value, total, style).forEachIndexed { index, filled ->
            Box(
                modifier = Modifier
                    .width(pitch.gridUnitsAsDp())
                    .then(if (tapSized) Modifier.height(ROW_HEIGHT_GRID_UNITS.gridUnitsAsDp()) else Modifier)
                    .then(if (tap != null) Modifier.lightClickable { tap(index) } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Mark(filled = filled, sizeUnits = diameter, strokeUnits = stroke)
            }
        }
    }
}
