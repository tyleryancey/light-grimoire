package dev.tyler.grimoire.ui.common

/**
 * Every grid-unit measurement the tool's own components draw with, in one file, each with the
 * arithmetic it came from.
 *
 * **This is the least testable and the most breakable part of the design.** A composable cannot be
 * exercised from the JVM gate, so nothing here is proved by a passing suite the way `pipStates` or
 * `slotStrip` are — only the derivations are ([Layout], pinned by `LayoutTest`). The numbers
 * themselves are answerable to the device and nothing else. **When device QA disagrees with a
 * budget — a row that clips, a pad that pushes the verb chips off the bottom, a pip strip that
 * wraps — this is the one file to edit.** Every component below takes its dimensions from here and
 * re-derives none of its own, so a corrected figure reaches S1, S3, S5, S6, S7, S8 and S9 at once.
 *
 * **The budget.** `LightGrid` is 27 × 31 units on the LP3 (1 unit ≈ 40 px ≈ 15.2 dp). The top bar
 * takes 3 units and the bottom bar 4 plus its own 1-unit top margin (`LightBottomBar.kt:18-20`),
 * leaving [CONTENT_HEIGHT_GRID_UNITS] of vertical room and, after each row's
 * [ROW_SIDE_MARGIN_UNITS] on both sides, at most 25 of the 27 columns — see [ROW_SIDE_MARGIN_UNITS]
 * for the two scroll views that take more.
 *
 * **Which converter a unit goes through.** `sdk:ui` ships two, and they are not the same function:
 * `Float.gridUnitsAsDp()` is `screenWidthDp / 27` and `Float.verticalGridUnitsAsDp()` is
 * `screenHeightDp / 31` (`LightGrid.kt:18-28`). On the LP3 they return the same length — 1080/27 =
 * 1240/31 = 40 px, the grid is square — which is why the tree already mixes them with no visible
 * difference: `Rows.kt` takes a row *height* through the horizontal helper, `ReaderScreen.kt:72`
 * takes a scroll *step* through the vertical one. The convention this file keeps is the one M2's
 * hardware QA actually measured: **every height and width below goes through `gridUnitsAsDp`**, and
 * only a scroll step stated against the 31-unit screen goes through `verticalGridUnitsAsDp`. The
 * line-box arithmetic in [Layout] is inherently *vertical* — it comes from `designVerticalPxToSp`'s
 * 600-px baseline — so a budget derived from it is a budget in vertical units, and spending it
 * against a height drawn with the horizontal helper is sound only because the grid is square. On a
 * Light device whose grid is not square, that is the first assumption to re-check.
 */

/** Content units between the bars: 31 − 3 (top bar) − 4 (bottom bar) − 1 (its top margin). */
const val CONTENT_HEIGHT_GRID_UNITS = 23f

/**
 * The margin every row keeps on both sides (`NavRow`'s `padding(horizontal = …)`).
 *
 * **25 of the grid's 27 columns is the best case, not the general one** — the scroll view a row sits in
 * spends a gutter of its own before this margin is taken, and there are three answers, from
 * `LightScrollView.kt`:
 *
 * - **Drawn outside any scroll view, or inside a lazy list that does not scroll**: nothing is spent, so
 *   27 − 2 = **25 usable**. S1's pinned header is the case that matters (it is drawn above the list, so
 *   the stat line really does get its 25); `LightLazyScrollView` pads by 0 while `showScrollBar` is false
 *   (`:175-179`).
 * - **Inside a `LightScrollView`** (S0, S3, S10): the 2-unit `Outside` gutter is padded off
 *   **unconditionally**, drawn bar or not (`:115-121`), so 27 − 2 − 2 = **23 usable**. `MarkdownBlocks`'
 *   table budget already states this half.
 * - **Inside a scrolling `LightLazyScrollView`** (S1's nine rows, S13's lists): the gutter is spent
 *   *twice* — the 2-unit `LightScrollBar` takes its place in the `Row` and the weighted `LazyColumn` is
 *   then padded by another 2 (`:213-229`) — so 27 − 2 − 2 − 2 = **21 usable**.
 *
 * `LightScrollBarPosition.Inside` costs no content width at all in either view (`scrollBarGutterUnits`
 * returns 0), which is why S3 takes it; a screen whose row is tight for width should reach for that before
 * it reaches for this margin.
 */
const val ROW_SIDE_MARGIN_UNITS = 1f

/**
 * UI-SPEC list-row height: 2.5 grid units, shared by every row so `LightLazyScrollView`'s
 * uniform-row contract holds. Taller than any single line box ([Layout.COPY_LINE_UNITS] is 2.33)
 * because it carries the tap target; 38 dp on the LP3, the tool's one row height.
 */
const val ROW_HEIGHT_GRID_UNITS = 2.5f

/**
 * S0's two-line character row: `Copy` name over a lightened `Detail` summary.
 * [Layout.COPY_LINE_UNITS] + [Layout.DETAIL_LINE_UNITS] = 3.82, rounded up to 4.0 for the 0.18 of
 * padding that keeps the two lines off each other — the figure S0's own budget is written with
 * (6 × 4 + a 1-unit gap + 3 × 2.5 = 32.5 units against 23, which is why S0 scrolls).
 */
const val TWO_LINE_ROW_HEIGHT_GRID_UNITS = 4f

/**
 * S1's pinned header: [SHEET_HEADER_PAD_UNITS] + the identity line + the stat line +
 * [SHEET_HEADER_PAD_UNITS], both lines `Detail`. 0.5 + 1.50 + 1.50 + 0.5 = 4.0 exactly.
 *
 * It has to be pinned and `Detail`-compact because the nine hub rows are 9 × 2.5 = 22.5 of the 23
 * content units on their own: a header of two `Copy` lines would be 4.65 and the screen would need
 * 27.15. With this header 19 units are left for the list, 19 / 2.5 = 7.6 rows visible.
 */
const val SHEET_HEADER_HEIGHT_GRID_UNITS = 4f

/** The half-unit pad above and below S1's two header lines — the `· · ·` the wireframe draws. */
const val SHEET_HEADER_PAD_UNITS = 0.5f

/**
 * S1's inspiration star, drawn at the end of the identity line. `LightIcon` defaults to 2 units square
 * ([NAV_ARROW_WIDTH_UNITS]), which is taller than the [Layout.DETAIL_LINE_UNITS] line it sits on and would
 * push the pinned header past its 4-unit budget; 1.3 leaves 0.2 of clearance inside that line box.
 */
const val SHEET_STAR_ICON_UNITS = 1.3f

/**
 * The star's tap box: [SHEET_STAR_ICON_UNITS] of glyph in a box exactly as wide as an interactive pip's cell
 * ([PIP_TAP_PITCH_UNITS], 1.7), and as tall as the [Layout.DETAIL_LINE_UNITS] identity line it shares —
 * ≈ 26 × 23 dp on the LP3.
 *
 * **Shorter than every other target in the tool, and on purpose.** A tappable pip is 26 × 38 dp; the star
 * matches its width and loses its height, because the header is 4 units and nothing in it may grow. Centring
 * the star between the two header lines instead would have made the box ≈ 26 × 61 dp — but the wireframe
 * draws `★` on the identity line, beside the thing it qualifies, and a mark floating between two lines would
 * read as belonging to neither. Position won. **If device QA finds 23 dp too short to hit, this is the figure
 * to raise — and raising it takes the star off the line**, which is a spec change, not a layout tweak.
 */
const val SHEET_STAR_TAP_WIDTH_UNITS = 1.7f

/**
 * The width a [NavRow]'s trailing `ARROW_RIGHT` occupies — `LightIcon`'s own default square
 * (`LightIcon.kt:20`), restated here because an arrow-less row has to reserve exactly that much to keep the
 * detail column's right edge steady down a list that mixes live and inert rows (S1).
 */
const val NAV_ARROW_WIDTH_UNITS = 2f

/** The blank line between two groups of rows (S0's characters and its utilities). */
const val SECTION_GAP_UNITS = 1f

/**
 * A display pip's disc, drawn inline with `Detail` text (S1's slot row, S2's rows, S6's counters,
 * S7's exhaustion strip, S8's hit dice). Three-quarters of the [PIP_PITCH_UNITS] cell, so a run
 * reads as the wireframes' tight `●●●●`.
 */
const val PIP_DIAMETER_UNITS = 0.5f

/**
 * The hairline every stroked mark in the tool is drawn with — a hollow pip's ring, an
 * [OutlineButton]'s border. 0.1 unit = 4 px ≈ 1.5 dp on the LP3. One weight, so a ring never reads
 * as a different mark from the disc it sits beside.
 */
const val PIP_STROKE_UNITS = 0.1f

/**
 * Centre-to-centre spacing of display pips: one `Detail` character's advance. The preamble's
 * conversion — `Detail` is 20 sp against `Copy`'s 30, so it packs [Layout.DETAIL_CHARS_PER_UNIT] as
 * many characters per unit — makes that 1 / 1.5 = 0.67 units, leaving 0.17 of gap around a
 * [PIP_DIAMETER_UNITS] disc.
 */
const val PIP_PITCH_UNITS = 0.67f

/**
 * An interactive pip's disc (S3's death saves, S5's spend-a-slot pips). Larger than the display
 * disc because it is a control the player aims at mid-combat rather than a glyph in a text line;
 * still well inside the [PIP_TAP_PITCH_UNITS] cell, so the strip reads as the spaced `○ ○ ○` S3's
 * wireframe draws rather than S1's tight run.
 */
const val PIP_TAP_DIAMETER_UNITS = 0.9f

/** The ring weight that goes with [PIP_TAP_DIAMETER_UNITS] — [PIP_STROKE_UNITS] scaled to the larger disc. */
const val PIP_TAP_STROKE_UNITS = 0.14f

/**
 * Centre-to-centre spacing of interactive pips, which is also the width of each pip's tap box; the
 * box is [ROW_HEIGHT_GRID_UNITS] tall, so a target is ≈ 26 × 38 dp.
 *
 * Derived from S5, the tightest tappable strip in the spec: three ordinal labels at `Detail`
 * (3 characters ≈ 2 units each) and two group gaps take ≈ 7 of the 25 usable columns, leaving 18
 * for a full caster's 4 + 3 + 3 = 10 pips, i.e. 1.8 units each. 1.7 keeps a unit of headroom.
 * S3's death row is looser (two labels and six pips afford ≈ 2.4 each) and uses the same figure, so
 * a pip that can be tapped is the same size everywhere in the tool.
 *
 * **That 25 is a constraint on S5, not an assumption it may quietly break**: 6 + 1 + 10 × 1.7 = 24 fits
 * 25 and does not fit the 21 a scrolling `Outside` lazy list leaves ([ROW_SIDE_MARGIN_UNITS]). S5 must
 * therefore draw its slot line where the full width is available — an `Inside` scrollbar, the way S3
 * already does, or outside the scroll view — or come back here and re-derive this figure.
 */
const val PIP_TAP_PITCH_UNITS = 1.7f

/**
 * An [OutlineButton]'s height — S3's `±n` pad and `[ ROLL DEATH SAVE ]`, S8's `[ROLL]`/`[AVG]`,
 * S15's `[ ROLL ]`. The `Button` line box is 30 × 1.10 × 31/600 = 1.71 units; 0.65 of pad above and
 * below makes 3.0 units ≈ 46 dp, deliberately taller than the 2.5-unit row because these are the
 * buttons a player hits without looking.
 *
 * S3 DYING is the state that has to afford three of them — `[ ROLL DEATH SAVE ]` and the pad's two
 * rows. Everything that state draws: a `Heading` status line (2.65), the death-save pip row (2.5),
 * those three buttons (9.0) with [PAD_ROW_GAP_UNITS] between the pad's own two (0.25), the
 * last-action line ([Layout.DETAIL_LINE_UNITS], 1.50) and the verb chips (2.5) come to **18.40** of
 * the 23 content units, leaving 4.60 for the gaps between them — of which
 * [HP_BLOCK_GAP_UNITS]' walk spends 4.00, for a total of 22.40 and 0.60 of slack.
 */
const val PAD_BUTTON_HEIGHT_UNITS = 3f

/** The corner radius of an [OutlineButton]'s stroked rectangle — the wireframes' `[ … ]` brackets, softened. */
const val PAD_BUTTON_CORNER_UNITS = 0.4f

/** The gap between two buttons in a pad row, so three equal-weight buttons read as three. */
const val PAD_BUTTON_GAP_UNITS = 0.5f

/** The gap between the `−n` row and the `+n` row of a [NumberPad] — half a button gap, since the rows pair. */
const val PAD_ROW_GAP_UNITS = 0.25f

/**
 * The gap between two blocks of S3's stacked column — the status line, the death panel, the pad, and
 * (in DEAD) the `[ REVIVE ]` button.
 *
 * S3 DYING is the tightest budget in the tool and this is the figure that absorbs a correction to it.
 * Reading the column top to bottom: [HP_TIGHT_GAP_UNITS] above the status line, the `Heading` status
 * line (2.65), a block gap, the death-save pip row ([ROW_HEIGHT_GRID_UNITS], 2.5), a tight gap,
 * `[ ROLL DEATH SAVE ]` ([PAD_BUTTON_HEIGHT_UNITS], 3.0), a block gap, the pad
 * (2 × 3.0 + [PAD_ROW_GAP_UNITS] = 6.25), a tight gap, the last-action line
 * ([Layout.DETAIL_LINE_UNITS], 1.50), a tight gap, the verb chips (2.5) — 22.4 of the
 * [CONTENT_HEIGHT_GRID_UNITS] available. STABLE spends the same, drawing its "no further saves" line in
 * a box the height of the roll button it replaces so the chrome does not move between the two states.
 *
 * The 0.6 of slack is the whole margin, which is why S3 draws inside a `LightScrollView`: a device that
 * rounds a line box up anywhere in that column scrolls by a few pixels instead of clipping the chips.
 */
const val HP_BLOCK_GAP_UNITS = 1f

/** The tighter gap between two parts of one of S3's blocks — a label and its pips, the pad and its line. */
const val HP_TIGHT_GAP_UNITS = 0.5f

/**
 * The `SELECT_ON`/`SELECT_OFF` glyph inside a [ChipRow] chip. `LightIcon` defaults to 2 units square
 * (`LightIcon.kt:20`), which crowds a [ROW_HEIGHT_GRID_UNITS] row that also carries a `Detail`
 * label; 1.4 leaves 0.55 of clearance above and below.
 */
const val CHIP_ICON_SIZE_UNITS = 1.4f

/** The gap between a chip's glyph and its label. */
const val CHIP_ICON_GAP_UNITS = 0.4f

/**
 * The line-box arithmetic every vertical budget in this file and in `docs/UI-SPEC.md` is derived
 * from, as pure functions the JVM gate can replay.
 *
 * The SDK multiplies its whole type scale by `screenHeightDp / 600` (`LightGrid.kt:32-36`) and the
 * grid is `screenHeightDp / 31` tall, so the screen-height factor and the pixel density cancel: one
 * line box in grid units is `fontSize × lineHeight × 31 / 600`, exact for any LP3 whatever its
 * density. The sp and multiple of each variant are `LightTheme.kt:85-136`.
 */
object Layout {
    /** The baseline `designVerticalPxToSp` divides by (`LightGrid.kt:30`). */
    const val TYPE_SCALE_BASELINE_PX = 600f

    /** The grid's vertical unit count — `LightGrid.HEIGHT`, restated so this object stays JVM-pure. */
    const val GRID_HEIGHT_UNITS = 31

    /**
     * How tall one line of type is, in grid units: `fontSize × lineHeight × 31 / 600`.
     * [fontSizeSp] and [lineHeight] are the variant's unscaled design figures from `LightTheme.kt`.
     */
    fun lineBoxUnits(fontSizeSp: Float, lineHeight: Float): Float =
        fontSizeSp * lineHeight * GRID_HEIGHT_UNITS / TYPE_SCALE_BASELINE_PX

    /** `Heading` — 38 sp × 1.35. S3's `DEAD` and its status line. */
    val HEADING_LINE_UNITS: Float = lineBoxUnits(38f, 1.35f)

    /** `Copy` — 30 sp × 1.50. The name line of every row. */
    val COPY_LINE_UNITS: Float = lineBoxUnits(30f, 1.50f)

    /** `Subheading` — 30 sp × 1.25. The same 30 sp as `Copy` at `FontWeight.Normal`: not "heavier type". */
    val SUBHEADING_LINE_UNITS: Float = lineBoxUnits(30f, 1.25f)

    /** `Paragraph` — 24.5 sp × 1.25. The reader's prose. */
    val PARAGRAPH_LINE_UNITS: Float = lineBoxUnits(24.5f, 1.25f)

    /** `Detail` — 20 sp × 1.45. S1's header lines, every pip strip's labels, a row's right column. */
    val DETAIL_LINE_UNITS: Float = lineBoxUnits(20f, 1.45f)

    /** `Fine` — 25 sp × 1.15. The top bar's centre. */
    val FINE_LINE_UNITS: Float = lineBoxUnits(25f, 1.15f)

    /**
     * How many more characters a `Detail` line fits than a `Copy` line: 30 / 20. The wireframes are
     * drawn one character per unit at `Copy`, so a `Detail` line holds 1.5 characters per unit —
     * the conversion [PIP_PITCH_UNITS] and the S5 budget behind [PIP_TAP_PITCH_UNITS] both use.
     */
    const val DETAIL_CHARS_PER_UNIT = 1.5f

    /** How many rows of a given height fit in the content area — S1's 7.6 of nine. */
    fun visibleRows(rowHeightUnits: Float, headerUnits: Float = 0f): Float =
        (CONTENT_HEIGHT_GRID_UNITS - headerUnits) / rowHeightUnits
}
