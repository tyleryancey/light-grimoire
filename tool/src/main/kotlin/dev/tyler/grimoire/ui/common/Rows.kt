package dev.tyler.grimoire.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

/**
 * The UI-SPEC `▸` navigating row (kind list, record list, search results): name in `Copy`
 * weighted to fill, an optional right-aligned lightened `Detail` (e.g. a count or a spell's
 * level), and the trailing `ARROW_RIGHT` nav glyph. Fixed [ROW_HEIGHT_GRID_UNITS] tall.
 */
@Composable
fun NavRow(
    name: String,
    detail: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT_GRID_UNITS.gridUnitsAsDp())
            .lightClickable { onClick() }
            .padding(horizontal = ROW_SIDE_MARGIN_UNITS.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(
            text = name,
            variant = LightTextVariant.Copy,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (detail != null) {
            LightText(
                text = detail,
                variant = LightTextVariant.Detail,
                // A gap, so an ellipsized name never butts straight against the detail column.
                modifier = Modifier.padding(start = 0.5f.gridUnitsAsDp()),
                lighten = true,
                maxLines = 1,
            )
        }
        LightIcon(LightIcons.ARROW_RIGHT)
    }
}

/**
 * S0's two-line character row: the name in `Copy` over a lightened `Detail` second line — the
 * character's `summary` verbatim, "Cleric 5 · Hill Dwarf" — and the trailing `ARROW_RIGHT` glyph.
 * Fixed [TWO_LINE_ROW_HEIGHT_GRID_UNITS] tall.
 *
 * This is deliberately **not** a `LightLazyScrollView` row: S0 mixes these 4-unit rows with 2.5-unit
 * utility rows, which the uniform-row contract cannot draw, so its list is a plain `LightScrollView`
 * (docs/UI-SPEC.md S0). Both lines ellipsize — a name runs to 40 characters and a summary carries
 * whatever race the player transcribed, so neither is allowed to wrap the row taller than its budget.
 */
@Composable
fun TwoLineRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TWO_LINE_ROW_HEIGHT_GRID_UNITS.gridUnitsAsDp())
            .lightClickable { onClick() }
            .padding(horizontal = ROW_SIDE_MARGIN_UNITS.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            LightText(
                text = title,
                variant = LightTextVariant.Copy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LightText(
                text = subtitle,
                variant = LightTextVariant.Detail,
                // The wireframe indents the summary under its name rather than drawing a bullet.
                modifier = Modifier.padding(start = ROW_SIDE_MARGIN_UNITS.gridUnitsAsDp()),
                lighten = true,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LightIcon(LightIcons.ARROW_RIGHT)
    }
}

/**
 * The UI-SPEC section header (e.g. the spells list's level bands): a lightened `Detail`
 * label — uppercased here, so every call site gets the spec's header treatment —
 * bottom-aligned in a non-clickable row of the same fixed [ROW_HEIGHT_GRID_UNITS] height,
 * keeping header rows inside the uniform-row contract of `LightLazyScrollView`. No
 * dividers, per the section-header recipe.
 */
@Composable
fun SectionHeaderRow(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT_GRID_UNITS.gridUnitsAsDp())
            .padding(horizontal = ROW_SIDE_MARGIN_UNITS.gridUnitsAsDp()),
        verticalAlignment = Alignment.Bottom,
    ) {
        LightText(
            text = label.uppercase(),
            variant = LightTextVariant.Detail,
            lighten = true,
            maxLines = 1,
        )
    }
}

/**
 * The one quiet line every screen uses for a state that is not a list: "Opening…", "Searching…",
 * "No matches.", "Not in the compendium." `sdk:ui` has no spinner, so a transient state is a line
 * of lightened `Copy` and nothing else — no illustration, no placeholder rows.
 */
@Composable
fun QuietLine(text: String, modifier: Modifier = Modifier) {
    LightText(
        text = text,
        variant = LightTextVariant.Copy,
        lighten = true,
        modifier = modifier.padding(1f.gridUnitsAsDp()),
    )
}
