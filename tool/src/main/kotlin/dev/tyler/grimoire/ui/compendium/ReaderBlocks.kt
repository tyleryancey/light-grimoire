package dev.tyler.grimoire.ui.compendium

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import dev.tyler.grimoire.compendium.Block
import dev.tyler.grimoire.compendium.CompendiumRef
import dev.tyler.grimoire.ui.common.MARKDOWN_SIDE_MARGIN_UNITS
import dev.tyler.grimoire.ui.common.MarkdownBlocks
import dev.tyler.grimoire.ui.common.ROW_HEIGHT_GRID_UNITS
import dev.tyler.grimoire.ui.common.SectionHeaderRow

/** Breathing room before the cross-link footer, and past the last row. */
private const val FOOTER_GAP_UNITS = 1.25f

/**
 * The S10 reader body: the composed [Block]s, then the resolved cross-link footer, inside one
 * `LightScrollView` (docs/UI-SPEC.md S10). The prose itself is [MarkdownBlocks], shared with S16
 * About; this file owns only what is compendium-shaped — the footer of resolved cross-links.
 *
 * The wheel is not read here: the screen owns [scrollState] and drives it from the view model's
 * ticks, so this composable stays a pure function of its arguments.
 */
@Composable
fun ReaderBody(
    blocks: List<Block>,
    links: List<ReaderLink>,
    scrollState: ScrollState,
    onLink: (CompendiumRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    LightScrollView(modifier = modifier, scrollState = scrollState) {
        MarkdownBlocks(blocks)
        if (links.isNotEmpty()) {
            Spacer(Modifier.height(FOOTER_GAP_UNITS.gridUnitsAsDp()))
            for (link in links) {
                SectionHeaderRow(link.label)
                for (ref in link.refs) {
                    LinkRow(ref = ref, onLink = onLink)
                }
            }
        }
        Spacer(Modifier.height(FOOTER_GAP_UNITS.gridUnitsAsDp()))
    }
}

/**
 * A "See: Prone" footer row: the ref's own name, underlined, pushing a chained reader when tapped.
 *
 * A FEATURES footer is otherwise full of rows that read alike — the barbarian's five "Ability Score
 * Improvement" and three "Path feature", the fighter's seven ASIs — so the ref's [level][CompendiumRef.level]
 * is drawn right-aligned and lightened, the `NavRow` detail treatment. Only spells and features carry a
 * level (`Rows.of`), and no footer query returns spells, so this is the class-level column and nothing else.
 */
@Composable
private fun LinkRow(ref: CompendiumRef, onLink: (CompendiumRef) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT_GRID_UNITS.gridUnitsAsDp())
            .lightClickable { onLink(ref) }
            .padding(horizontal = MARKDOWN_SIDE_MARGIN_UNITS.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(
            text = ref.name,
            variant = LightTextVariant.Copy,
            underline = true,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val level = ref.level
        if (level != null) {
            LightText(
                text = level.toString(),
                variant = LightTextVariant.Detail,
                lighten = true,
                maxLines = 1,
            )
        }
    }
}
