package dev.tyler.grimoire.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp

/**
 * One line of S1's pinned header — the identity line (`Cleric 5 · Hill Dwarf`) or the stat line
 * (`AC 18  INIT +0  SPD 25  PB +3`), and S5's `WIS · DC 15 · ATK +7`.
 *
 * `Detail`, one line, ellipsized, on the same [ROW_SIDE_MARGIN_UNITS] as every row beneath it. It
 * sets no height of its own: a `Detail` line box is [Layout.DETAIL_LINE_UNITS] ≈ 1.5 units, and two
 * of them plus the half-unit pads are exactly the [SHEET_HEADER_HEIGHT_GRID_UNITS] S1's budget
 * spends. `Detail` is not a stylistic choice there — nine 2.5-unit rows take 22.5 of the 23 content
 * units, so the header has 4 to live in and two `Copy` lines would need 4.65.
 *
 * The line is composed by the caller, whose separators are the wireframes' own (`·` between
 * identity fields, double spaces between stats), and its modifiers come from [Signs] so every `+2`
 * and `−1` in the tool is formatted in one place.
 */
@Composable
fun StatHeaderLine(text: String, modifier: Modifier = Modifier) {
    LightText(
        text = text,
        variant = LightTextVariant.Detail,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ROW_SIDE_MARGIN_UNITS.gridUnitsAsDp()),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** How the tool writes a signed number. */
object Signs {
    /**
     * A modifier as a character sheet prints it: `+3`, `+0`, `−1`. Zero is written `+0`, not `0` —
     * a sheet's ability, save and skill columns are always signed, and an unsigned entry in a column
     * of signed ones reads as a missing value rather than a zero one.
     *
     * The minus is U+2212 MINUS SIGN, not the hyphen-minus a keyboard types: at the sizes S1 and S4
     * draw modifiers, a hyphen sits high and short beside a `+` of the same run, and the columns
     * stop lining up. The same formatter labels [NumberPad]'s buttons, so `−10` on the pad and `−1`
     * in the stat line are the same glyph.
     */
    // toLong() before negating, not `-n`: negating Int.MIN_VALUE in place wraps back to itself and
    // would print a minus sign in front of a negative number.
    fun mod(n: Int): String = if (n < 0) "−${-n.toLong()}" else "+$n"
}
