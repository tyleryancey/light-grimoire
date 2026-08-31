package dev.tyler.grimoire.ui.compendium

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.designVerticalPxToSp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import dev.tyler.grimoire.compendium.Block
import dev.tyler.grimoire.compendium.CompendiumRef
import dev.tyler.grimoire.compendium.Span
import dev.tyler.grimoire.compendium.TableLayout
import dev.tyler.grimoire.ui.common.ROW_HEIGHT_GRID_UNITS
import dev.tyler.grimoire.ui.common.SectionHeaderRow

/** Side margin of the reading column, matching the list rows'. */
private const val SIDE_MARGIN_UNITS = 1f

/**
 * Side margin of a monospace table line, narrower than the prose's so the widest bundled table still fits.
 *
 * The budget is fixed arithmetic, not taste. `LightScrollView` reserves its 2-unit Outside scrollbar
 * gutter whether or not the bar is drawn, leaving 25 of the grid's 27 units; the prose's 1-unit margins
 * would take that to 23, and a 23-unit line holds only 46 monospace characters at `Superfine`
 * (0.6 em advance, the type scale's screenHeight/600 factor, both densities alike) against the
 * [TableLayout.GRID_COMPACT_MAX] of 48 — 120 of the 334 creature ability grids pack to 47 or 48 and would
 * lose their CHA cell. At 0.5 the line gets the 24 units the UI-SPEC's "≈ 38 chars / ≈ 48" was written
 * for: 48.4 characters at `Superfine`, 38.7 at `Detail`. Widening this back to 1 truncates real tables.
 */
private const val MONO_SIDE_MARGIN_UNITS = 0.5f

/** Space above an ordinary block; a heading opens a section, so it gets [HEADING_GAP_UNITS]. */
private const val BLOCK_GAP_UNITS = 0.35f

private const val HEADING_GAP_UNITS = 0.9f

/** The hanging-indent cell of a bullet or numbered item — wide enough for "10." at Paragraph size. */
private const val MARKER_WIDTH_UNITS = 2f

/** Breathing room before the cross-link footer, and past the last row. */
private const val FOOTER_GAP_UNITS = 1.25f

/**
 * The S10 reader body: the composed [Block]s, then the resolved cross-link footer, inside one
 * `LightScrollView` (docs/UI-SPEC.md S10). Mixed row heights are deliberate — this is the plain
 * scroll view, not `LightLazyScrollView`'s uniform-row list — and nothing scrolls horizontally.
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
        Spacer(Modifier.height(BLOCK_GAP_UNITS.gridUnitsAsDp()))
        blocks.forEachIndexed { i, block ->
            // A Mono run is one column-aligned table and stacks tight, so the gap goes before the run
            // rather than on every line of it — otherwise a creature's ability grid abuts its Speed line.
            if (block is Block.Mono && blocks.getOrNull(i - 1) !is Block.Mono) {
                Spacer(Modifier.height(BLOCK_GAP_UNITS.gridUnitsAsDp()))
            }
            ReaderBlock(block)
        }
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
 * One block, per the UI-SPEC's Markdown-lite render rules. The `when` is exhaustive on purpose:
 * a new [Block] variant must be given a rendering here rather than silently vanishing.
 */
@Composable
private fun ReaderBlock(block: Block) {
    val side = SIDE_MARGIN_UNITS.gridUnitsAsDp()
    when (block) {
        is Block.Heading -> when (block.level) {
            1, 2 -> LightText(
                text = plain(block.spans),
                variant = LightTextVariant.Heading,
                modifier = Modifier.padding(top = HEADING_GAP_UNITS.gridUnitsAsDp(), start = side, end = side),
            )
            3 -> LightText(
                text = plain(block.spans),
                variant = LightTextVariant.Subheading,
                modifier = Modifier.padding(top = HEADING_GAP_UNITS.gridUnitsAsDp(), start = side, end = side),
            )
            // h4/h5 are label lines inside a section: the paragraph size, every run bold.
            else -> InlineSpans(
                spans = block.spans,
                forceBold = true,
                modifier = Modifier.padding(top = HEADING_GAP_UNITS.gridUnitsAsDp(), start = side, end = side),
            )
        }

        is Block.Para -> InlineSpans(
            spans = block.spans,
            modifier = Modifier.padding(top = BLOCK_GAP_UNITS.gridUnitsAsDp(), start = side, end = side),
        )

        is Block.Bullet -> HangingRow(marker = "•", spans = block.spans)

        is Block.Numbered -> HangingRow(marker = "${block.number}.", spans = block.spans)

        is Block.Field -> LightText(
            text = block.text,
            variant = LightTextVariant.Paragraph,
            lighten = block.secondary,
            modifier = Modifier.padding(top = BLOCK_GAP_UNITS.gridUnitsAsDp(), start = side, end = side),
        )

        // One column-aligned table line, stacked tight against its neighbours (ReaderBody opens the
        // run), inside the wider MONO_SIDE_MARGIN_UNITS column its 38/48-character budgets assume.
        // maxLines = 1 keeps a line the arithmetic did not foresee from wrapping and breaking the column
        // alignment of every row under it; `LightText` exposes no softWrap, so such a line would lose its
        // last whole cell rather than a glyph — which is why the margin, not this, is the guard.
        is Block.Mono -> LightText(
            text = block.text,
            variant = if (block.compact) LightTextVariant.Superfine else LightTextVariant.Detail,
            monospace = true,
            lighten = block.secondary,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(horizontal = MONO_SIDE_MARGIN_UNITS.gridUnitsAsDp()),
        )

        // ReaderContent lowers every table before the screen sees it; this is the belt and braces.
        is Block.Table -> for (lowered in TableLayout.lower(block)) {
            ReaderBlock(lowered)
        }
    }
}

/** A bullet or numbered item: the marker in a fixed cell so wrapped lines align past it. */
@Composable
private fun HangingRow(marker: String, spans: List<Span>) {
    val side = SIDE_MARGIN_UNITS.gridUnitsAsDp()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = BLOCK_GAP_UNITS.gridUnitsAsDp(), start = side, end = side),
    ) {
        LightText(
            text = marker,
            variant = LightTextVariant.Paragraph,
            modifier = Modifier.width(MARKER_WIDTH_UNITS.gridUnitsAsDp()),
        )
        InlineSpans(spans = spans, modifier = Modifier.weight(1f))
    }
}

/**
 * Inline prose. Unstyled text takes the `sdk:ui` path so it is styled exactly like every other
 * paragraph in the tool; anything with emphasis (or a line break inside the paragraph) needs
 * per-run styling, which only `BasicText` over an `AnnotatedString` can express — sdk:ui has no
 * rich-text component.
 */
@Composable
private fun InlineSpans(spans: List<Span>, modifier: Modifier = Modifier, forceBold: Boolean = false) {
    val simple = !forceBold && spans.all { it is Span.Text && !it.bold && !it.italic }
    if (simple) {
        LightText(text = plain(spans), variant = LightTextVariant.Paragraph, modifier = modifier)
        return
    }
    val style = readerParagraphStyle()
    BasicText(
        text = buildAnnotatedString {
            for (span in spans) {
                when (span) {
                    is Span.Text -> withStyle(
                        SpanStyle(
                            fontWeight = if (span.bold || forceBold) FontWeight.Bold else null,
                            fontStyle = if (span.italic) FontStyle.Italic else null,
                        ),
                    ) {
                        append(span.text)
                    }
                    Span.LineBreak -> append("\n")
                }
            }
        },
        modifier = modifier,
        style = style,
    )
}

/**
 * The `Paragraph` variant's real style, ready for `BasicText`.
 *
 * `LightText` reaches this by calling `TextStyle.scaledForScreenHeight()`, which is `internal` to
 * `:sdk:ui` (LightText.kt) and unreachable from here, so the same arithmetic is redone with the
 * public `designVerticalPxToSp` — including its guard: `paragraph` sets no letter spacing, and an
 * `Unspecified` unit must stay unspecified rather than become a NaN size. The colour is set on the
 * style because `BasicText`, unlike `LightText`, inherits none.
 */
@Composable
private fun readerParagraphStyle(): TextStyle {
    val base = LightThemeTokens.typography.paragraph
    return base.copy(
        color = LightThemeTokens.colors.content,
        fontSize = base.fontSize.scaledForScreen(),
        lineHeight = base.lineHeight.scaledForScreen(),
        letterSpacing = base.letterSpacing.scaledForScreen(),
    )
}

@Composable
private fun TextUnit.scaledForScreen(): TextUnit =
    if (this == TextUnit.Unspecified) this else value.designVerticalPxToSp()

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
            .padding(horizontal = SIDE_MARGIN_UNITS.gridUnitsAsDp()),
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

/** The concatenated text of [spans]; a line break inside a heading or a plain run is a space. */
private fun plain(spans: List<Span>): String = buildString {
    for (span in spans) {
        when (span) {
            is Span.Text -> append(span.text)
            Span.LineBreak -> append(' ')
        }
    }
}
