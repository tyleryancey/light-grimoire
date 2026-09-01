package dev.tyler.grimoire.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.designVerticalPxToSp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import dev.tyler.grimoire.compendium.Block
import dev.tyler.grimoire.compendium.Span
import dev.tyler.grimoire.compendium.TableLayout

/**
 * Side margin of a reading column, matching the list rows'. Shared with the callers that draw their own
 * rows beside the prose (S10's cross-link footer), so a link row lines up with the paragraph above it.
 */
const val MARKDOWN_SIDE_MARGIN_UNITS = 1f

/** Space above an ordinary block; a heading opens a section, so it gets [HEADING_GAP_UNITS]. */
const val MARKDOWN_BLOCK_GAP_UNITS = 0.35f

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

private const val HEADING_GAP_UNITS = 0.9f

/** The hanging-indent cell of a bullet or numbered item — wide enough for "10." at Paragraph size. */
private const val MARKER_WIDTH_UNITS = 2f

/**
 * How many lines a block may occupy — this renderer's whole truncation policy, in one function so the
 * licensing test can pin it from the JVM gate.
 *
 * **Prose is never clipped.** S16 About draws the CC-BY-4.0 attribution through this same renderer, and
 * docs/LICENSING.md requires that wording verbatim, so a `maxLines` added to the Heading or Para branch for
 * S10's benefit would cut the licence sentence off on device while every JVM test stayed green. Only
 * [Block.Mono] clips, and only to stop one mis-measured table line from wrapping and breaking the column
 * alignment of every row under it — the [MONO_SIDE_MARGIN_UNITS] budget is the real guard there, this is the
 * belt. [Block.Table] is lowered to [Block.Mono] lines before anything is drawn, so its own answer is never
 * used. `AboutViewModelTest` asserts both answers; change them there or not at all.
 */
internal fun maxLinesOf(block: Block): Int = if (block is Block.Mono) 1 else Int.MAX_VALUE

/**
 * A run of Markdown-lite [Block]s, drawn per the UI-SPEC's render rules: the S10 reader's body and the
 * S16 About screen's attribution are the same prose renderer, so a change to either shows up in both.
 *
 * Emits siblings into the caller's column (both call sites are inside a `LightScrollView`), opening with
 * the same leading gap so neither screen's first paragraph sits against the top bar. Mixed row heights are
 * deliberate — this is the plain scroll view, not `LightLazyScrollView`'s uniform-row list — and nothing
 * scrolls horizontally. Pure function of [blocks]: no wheel, no state, no navigation.
 */
@Composable
fun MarkdownBlocks(blocks: List<Block>) {
    Spacer(Modifier.height(MARKDOWN_BLOCK_GAP_UNITS.gridUnitsAsDp()))
    blocks.forEachIndexed { i, block ->
        // A Mono run is one column-aligned table and stacks tight, so the gap goes before the run
        // rather than on every line of it — otherwise a creature's ability grid abuts its Speed line.
        if (block is Block.Mono && blocks.getOrNull(i - 1) !is Block.Mono) {
            Spacer(Modifier.height(MARKDOWN_BLOCK_GAP_UNITS.gridUnitsAsDp()))
        }
        MarkdownBlock(block)
    }
}

/**
 * One block, per the UI-SPEC's Markdown-lite render rules. The `when` is exhaustive on purpose:
 * a new [Block] variant must be given a rendering here rather than silently vanishing.
 *
 * Headings are drawn with their own text: uppercasing is a wireframe drawing convention, not a render
 * rule, and the About screen's `# Attribution` reads as written.
 */
@Composable
private fun MarkdownBlock(block: Block) {
    val side = MARKDOWN_SIDE_MARGIN_UNITS.gridUnitsAsDp()
    // Every branch takes its line budget from the one policy function rather than writing its own, so a
    // change of mind about truncation is a change to [maxLinesOf] — which the licensing test pins.
    val maxLines = maxLinesOf(block)
    when (block) {
        is Block.Heading -> when (block.level) {
            1, 2 -> LightText(
                text = plain(block.spans),
                variant = LightTextVariant.Heading,
                maxLines = maxLines,
                modifier = Modifier.padding(top = HEADING_GAP_UNITS.gridUnitsAsDp(), start = side, end = side),
            )
            3 -> LightText(
                text = plain(block.spans),
                variant = LightTextVariant.Subheading,
                maxLines = maxLines,
                modifier = Modifier.padding(top = HEADING_GAP_UNITS.gridUnitsAsDp(), start = side, end = side),
            )
            // h4/h5 are label lines inside a section: the paragraph size, every run bold.
            else -> InlineSpans(
                spans = block.spans,
                forceBold = true,
                maxLines = maxLines,
                modifier = Modifier.padding(top = HEADING_GAP_UNITS.gridUnitsAsDp(), start = side, end = side),
            )
        }

        is Block.Para -> InlineSpans(
            spans = block.spans,
            maxLines = maxLines,
            modifier = Modifier.padding(top = MARKDOWN_BLOCK_GAP_UNITS.gridUnitsAsDp(), start = side, end = side),
        )

        is Block.Bullet -> HangingRow(marker = "•", spans = block.spans, maxLines = maxLines)

        is Block.Numbered -> HangingRow(marker = "${block.number}.", spans = block.spans, maxLines = maxLines)

        is Block.Field -> LightText(
            text = block.text,
            variant = LightTextVariant.Paragraph,
            lighten = block.secondary,
            maxLines = maxLines,
            modifier = Modifier.padding(top = MARKDOWN_BLOCK_GAP_UNITS.gridUnitsAsDp(), start = side, end = side),
        )

        // One column-aligned table line, stacked tight against its neighbours ([MarkdownBlocks] opens the
        // run), inside the wider MONO_SIDE_MARGIN_UNITS column its 38/48-character budgets assume.
        // This is the one block [maxLinesOf] clips: one line keeps a row the arithmetic did not foresee
        // from wrapping and breaking the column alignment of every row under it; `LightText` exposes no
        // softWrap, so such a line would lose its last whole cell rather than a glyph — which is why the
        // margin, not this, is the guard.
        is Block.Mono -> LightText(
            text = block.text,
            variant = if (block.compact) LightTextVariant.Superfine else LightTextVariant.Detail,
            monospace = true,
            lighten = block.secondary,
            maxLines = maxLines,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(horizontal = MONO_SIDE_MARGIN_UNITS.gridUnitsAsDp()),
        )

        // ReaderContent lowers every table before the screen sees it; this is the belt and braces.
        is Block.Table -> for (lowered in TableLayout.lower(block)) {
            MarkdownBlock(lowered)
        }
    }
}

/** A bullet or numbered item: the marker in a fixed cell so wrapped lines align past it. */
@Composable
private fun HangingRow(marker: String, spans: List<Span>, maxLines: Int) {
    val side = MARKDOWN_SIDE_MARGIN_UNITS.gridUnitsAsDp()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MARKDOWN_BLOCK_GAP_UNITS.gridUnitsAsDp(), start = side, end = side),
    ) {
        // The marker is one short cell by construction ("•", "10.") — the item's own budget is the prose's.
        LightText(
            text = marker,
            variant = LightTextVariant.Paragraph,
            modifier = Modifier.width(MARKER_WIDTH_UNITS.gridUnitsAsDp()),
        )
        InlineSpans(spans = spans, maxLines = maxLines, modifier = Modifier.weight(1f))
    }
}

/**
 * Inline prose. Unstyled text takes the `sdk:ui` path so it is styled exactly like every other
 * paragraph in the tool; anything with emphasis (or a line break inside the paragraph) needs
 * per-run styling, which only `BasicText` over an `AnnotatedString` can express — sdk:ui has no
 * rich-text component.
 */
@Composable
private fun InlineSpans(
    spans: List<Span>,
    maxLines: Int,
    modifier: Modifier = Modifier,
    forceBold: Boolean = false,
) {
    val simple = !forceBold && spans.all { it is Span.Text && !it.bold && !it.italic }
    if (simple) {
        LightText(text = plain(spans), variant = LightTextVariant.Paragraph, maxLines = maxLines, modifier = modifier)
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
        overflow = TextOverflow.Clip,
        maxLines = maxLines,
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
 * The concatenated text of [spans]; a line break inside a heading or a plain run is a space.
 *
 * `internal` rather than private so `AboutViewModelTest` can read the bundled attribution back through the
 * renderer's own flattening instead of a copy of it — a copy would keep passing while this one changed
 * (docs/LICENSING.md: the CC-BY sentence must reach the screen verbatim).
 */
internal fun plain(spans: List<Span>): String = buildString {
    for (span in spans) {
        when (span) {
            is Span.Text -> append(span.text)
            Span.LineBreak -> append(' ')
        }
    }
}
