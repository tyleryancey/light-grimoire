package dev.tyler.grimoire.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
 * A segmented single-select row — S3's `DAMAGE  HEAL  TEMP` verb chips, and whatever else needs one
 * of a short fixed set chosen.
 *
 * One [ROW_HEIGHT_GRID_UNITS] row, each chip an equal-weight clickable box holding
 * `SELECT_ON`/`SELECT_OFF` and a `Detail` label. `sdk:ui` has no tab row, no segmented control and
 * no toggle; these two glyphs are the SDK's own selected/unselected pair, so the state reads the
 * way it reads everywhere else on the phone.
 *
 * **State is carried by the glyph alone** — every label stays at full content weight, selected or
 * not. Lightening the unselected labels would say the same thing twice, and the wireframe draws
 * `DAMAGE HEAL TEMP` in one weight under `[■] [□] [□]`.
 *
 * [selected] outside `labels.indices` simply selects nothing, which is the honest drawing of a
 * caller that has not chosen yet.
 */
@Composable
fun ChipRow(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT_GRID_UNITS.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .lightClickable { onSelect(index) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                LightIcon(
                    icon = if (index == selected) LightIcons.SELECT_ON else LightIcons.SELECT_OFF,
                    size = CHIP_ICON_SIZE_UNITS,
                )
                Spacer(Modifier.width(CHIP_ICON_GAP_UNITS.gridUnitsAsDp()))
                LightText(
                    text = label,
                    variant = LightTextVariant.Detail,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
