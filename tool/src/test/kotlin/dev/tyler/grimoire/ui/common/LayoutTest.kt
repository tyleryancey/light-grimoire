package dev.tyler.grimoire.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The grid arithmetic behind `Layout.kt`. A composable cannot be exercised from this gate, so what
 * is pinned here is the *derivation* of each constant: the line-box formula the UI-SPEC preamble's
 * table comes from, and the screen budgets that decide whether a number is affordable. Change a
 * constant because the device disagreed with it and this test tells you which budget you just spent.
 */
class LayoutTest {
    private val tolerance = 0.01f

    private fun assertClose(expected: Float, actual: Float, message: String) =
        assertTrue(kotlin.math.abs(expected - actual) <= tolerance, "$message — expected ≈ $expected, was $actual")

    // ---- the line-box table ----------------------------------------------------------------------------------

    @Test
    fun oneLineBoxIsFontSizeTimesLineHeightTimesThirtyOneOverSixHundred() {
        // The UI-SPEC preamble's table, variant by variant (sp × lineHeight from LightTheme.kt:85-136).
        assertClose(2.65f, Layout.HEADING_LINE_UNITS, "Heading, 38 × 1.35")
        assertClose(2.33f, Layout.COPY_LINE_UNITS, "Copy, 30 × 1.50")
        assertClose(1.94f, Layout.SUBHEADING_LINE_UNITS, "Subheading, 30 × 1.25")
        assertClose(1.58f, Layout.PARAGRAPH_LINE_UNITS, "Paragraph, 24.5 × 1.25")
        assertClose(1.50f, Layout.DETAIL_LINE_UNITS, "Detail, 20 × 1.45")
        assertClose(1.49f, Layout.FINE_LINE_UNITS, "Fine, 25 × 1.15")
    }

    @Test
    fun subheadingIsNotHeavierThanCopyItIsOnlyTighter() {
        // Both variants are 30 sp FontWeight.Normal (LightTheme.kt:91-102): the whole reason S1's
        // bloodied HP needs EmphasisText's bold span instead of a swap to Subheading.
        assertClose(Layout.lineBoxUnits(30f, 1.25f), Layout.SUBHEADING_LINE_UNITS, "Subheading's box")
        assertTrue(Layout.SUBHEADING_LINE_UNITS < Layout.COPY_LINE_UNITS, "tighter, not larger")
    }

    @Test
    fun headingIsGenuinelyLargerThanCopy() {
        // S3's DEAD label needs no bold help, unlike S1's bloodied numbers: 38 sp against 30.
        assertTrue(Layout.HEADING_LINE_UNITS > Layout.COPY_LINE_UNITS, "Heading really is bigger type")
    }

    // ---- row heights -----------------------------------------------------------------------------------------

    @Test
    fun theRowHeightClearsTheLineBoxItCarries() {
        assertEquals(2.5f, ROW_HEIGHT_GRID_UNITS, "the tool's one row height")
        assertTrue(ROW_HEIGHT_GRID_UNITS > Layout.COPY_LINE_UNITS, "a Copy name fits with room for the tap target")
    }

    @Test
    fun theTwoLineRowHoldsACopyLineOverADetailLine() {
        val content = Layout.COPY_LINE_UNITS + Layout.DETAIL_LINE_UNITS
        assertTrue(TWO_LINE_ROW_HEIGHT_GRID_UNITS >= content, "S0's name over its summary fits: $content")
        assertTrue(
            TWO_LINE_ROW_HEIGHT_GRID_UNITS - content < 0.25f,
            "and not by so much that the row is mostly padding",
        )
    }

    @Test
    fun theSheetHeaderIsTwoDetailLinesBetweenTwoHalfUnitPads() {
        val derived = 2 * SHEET_HEADER_PAD_UNITS + 2 * Layout.DETAIL_LINE_UNITS
        assertClose(SHEET_HEADER_HEIGHT_GRID_UNITS, derived, "S1's pinned header, 0.5 + 1.5 + 1.5 + 0.5")
    }

    @Test
    fun theInspirationStarFitsOnTheLineItSitsOn() {
        // The star is drawn beside the identity line, inside that line's own box — LightIcon's 2-unit default
        // would push the pinned header past its 4-unit budget. The tap box is as wide as an interactive pip's
        // cell; its height is the line's, which is the price of keeping the star on the line the frame draws it on.
        assertTrue(
            SHEET_STAR_ICON_UNITS < Layout.DETAIL_LINE_UNITS,
            "the glyph sits inside a Detail line box (${Layout.DETAIL_LINE_UNITS}), unlike LightIcon's $NAV_ARROW_WIDTH_UNITS default",
        )
        assertTrue(SHEET_STAR_TAP_WIDTH_UNITS >= PIP_TAP_PITCH_UNITS, "and its target is no narrower than a tappable pip's")
        assertTrue(SHEET_STAR_TAP_WIDTH_UNITS > SHEET_STAR_ICON_UNITS, "with the glyph centred inside it")
    }

    @Test
    fun anInertRowStillSpendsTheArrowsWidth() {
        // NavRow reserves NAV_ARROW_WIDTH_UNITS when it draws no arrow, so a list mixing live and inert rows
        // (S1's, until S2-S9 land) keeps one right-hand edge. The figure is LightIcon's own default square.
        assertEquals(2f, NAV_ARROW_WIDTH_UNITS, "LightIcon.kt:20's DEFAULT_SIZE")
        assertTrue(NAV_ARROW_WIDTH_UNITS < ROW_HEIGHT_GRID_UNITS, "and the glyph fits the row it trails")
    }

    @Test
    fun aCopyWeightSheetHeaderWouldNotFitWhichIsWhyItIsDetail() {
        val copyHeader = 2 * SHEET_HEADER_PAD_UNITS + 2 * Layout.COPY_LINE_UNITS
        val nineRows = 9 * ROW_HEIGHT_GRID_UNITS
        assertTrue(copyHeader + nineRows > CONTENT_HEIGHT_GRID_UNITS, "27.15 units against 23")
        assertTrue(
            SHEET_HEADER_HEIGHT_GRID_UNITS + nineRows > CONTENT_HEIGHT_GRID_UNITS,
            "even the Detail header overflows — which is why S1's list scrolls under a pinned header",
        )
    }

    // ---- screen budgets --------------------------------------------------------------------------------------

    @Test
    fun sevenOfS1sNineRowsAreVisibleOnOpen() {
        val visible = Layout.visibleRows(ROW_HEIGHT_GRID_UNITS, SHEET_HEADER_HEIGHT_GRID_UNITS)
        assertClose(7.6f, visible, "19 units of list at 2.5 a row")
        assertTrue(visible >= 7f, "the seven rows the one-tap contract names are all above the fold")
        assertTrue(visible < 9f, "and the last two are a wheel turn away, which is why the list scrolls")
    }

    @Test
    fun s0DoesNotFitOnOneScreenAndMixesRowHeights() {
        val characters = 6 * TWO_LINE_ROW_HEIGHT_GRID_UNITS
        val utilities = 3 * ROW_HEIGHT_GRID_UNITS
        val total = characters + SECTION_GAP_UNITS + utilities
        assertClose(32.5f, total, "six characters, a gap and three utilities")
        assertTrue(total > CONTENT_HEIGHT_GRID_UNITS, "so S0 scrolls")
        assertTrue(
            TWO_LINE_ROW_HEIGHT_GRID_UNITS != ROW_HEIGHT_GRID_UNITS,
            "and mixes heights, which LightLazyScrollView's uniform-row contract cannot draw",
        )
    }

    @Test
    fun s3sFullestStateFitsWithRoomForItsGaps() {
        // Everything DYING draws: a Heading status line, the death-save pip row, [ ROLL DEATH SAVE ] and
        // the pad's two rows, the last-action line, the verb chips. A *partial* figure here is what let
        // PAD_BUTTON_HEIGHT_UNITS' KDoc drift from HP_BLOCK_GAP_UNITS' walk, so this counts them all.
        val content = Layout.HEADING_LINE_UNITS +
            ROW_HEIGHT_GRID_UNITS +
            3 * PAD_BUTTON_HEIGHT_UNITS +
            PAD_ROW_GAP_UNITS +
            Layout.DETAIL_LINE_UNITS +
            ROW_HEIGHT_GRID_UNITS
        assertClose(18.40f, content, "S3 DYING's drawn content")
        val slack = CONTENT_HEIGHT_GRID_UNITS - content
        assertTrue(slack >= 4.5f, "the column's six gaps still fit: $slack left")
    }

    @Test
    fun s3sDyingColumnIsTheTwelveTermWalkItsKdocClaims() {
        // The walk HP_BLOCK_GAP_UNITS' KDoc spells out, top to bottom, built from the named constants —
        // so the documented arithmetic and the test are one thing and cannot disagree.
        val column = HP_TIGHT_GAP_UNITS +
            Layout.HEADING_LINE_UNITS +
            HP_BLOCK_GAP_UNITS +
            ROW_HEIGHT_GRID_UNITS +
            HP_TIGHT_GAP_UNITS +
            PAD_BUTTON_HEIGHT_UNITS +
            HP_BLOCK_GAP_UNITS +
            (2 * PAD_BUTTON_HEIGHT_UNITS + PAD_ROW_GAP_UNITS) +
            HP_TIGHT_GAP_UNITS +
            Layout.DETAIL_LINE_UNITS +
            HP_TIGHT_GAP_UNITS +
            ROW_HEIGHT_GRID_UNITS
        assertClose(22.40f, column, "S3 DYING, the tightest budget in the tool")
        assertTrue(column <= CONTENT_HEIGHT_GRID_UNITS, "it fits the 23 content units: $column")
        assertTrue(
            CONTENT_HEIGHT_GRID_UNITS - column < 1f,
            "with under a unit of slack, which is why S3 draws inside a LightScrollView",
        )
    }

    // ---- pip geometry ----------------------------------------------------------------------------------------

    @Test
    fun aDisplayPipSitsInsideOneDetailCharactersAdvance() {
        assertClose(1f / Layout.DETAIL_CHARS_PER_UNIT, PIP_PITCH_UNITS, "the pitch is one Detail character")
        assertTrue(PIP_DIAMETER_UNITS < PIP_PITCH_UNITS, "so a run reads tight but never touches")
    }

    @Test
    fun everyStrokeIsThinnerThanHalfTheMarkItOutlines() {
        // The ring is drawn at radius (edge − stroke) / 2; a stroke past half the diameter would
        // invert it into a blot with the same outer edge as the filled disc.
        assertTrue(PIP_STROKE_UNITS < PIP_DIAMETER_UNITS / 2f, "display pip")
        assertTrue(PIP_TAP_STROKE_UNITS < PIP_TAP_DIAMETER_UNITS / 2f, "interactive pip")
    }

    @Test
    fun anInteractivePipIsBiggerThanADisplayOneAndStillFitsS5sLine() {
        assertTrue(PIP_TAP_DIAMETER_UNITS > PIP_DIAMETER_UNITS, "a control is aimed at, a glyph is read")
        assertTrue(PIP_TAP_DIAMETER_UNITS < PIP_TAP_PITCH_UNITS, "and still sits inside its tap box")
        // S5's worst case: three ordinal labels, two group gaps and a full caster's 4 + 3 + 3 pips.
        // The 25 here is the *full* row width, which holds only where no scrollbar gutter is spent —
        // S3's `Inside` bar, or a list that does not scroll (ROW_SIDE_MARGIN_UNITS derives all three
        // cases). A scrolling `Outside` lazy list would leave 21, and this line does not fit in 21: that
        // is a constraint on how S5 draws its strip, recorded here rather than discovered on the phone.
        val usable = 27f - 2 * ROW_SIDE_MARGIN_UNITS
        val labels = 3 * (3f / Layout.DETAIL_CHARS_PER_UNIT)
        val line = labels + 2 * 0.5f + 10 * PIP_TAP_PITCH_UNITS
        assertTrue(line <= usable, "S5's slot line fits the 25 usable columns: $line")
    }

    @Test
    fun s3sDeathSaveRowIsLooserThanS5sSlotLine() {
        // "success" and "failure" at Detail, plus a gap between the two groups, plus six pips. This is the
        // row in *both* DYING and STABLE: STABLE passes `tapSized = true` so its pips keep this pitch when
        // the handler goes away, which is what stops the six discs jumping as the third success lands.
        // S3 really does get all 25: its `LightScrollView` takes `LightScrollBarPosition.Inside`, which
        // costs no content width (`scrollBarGutterUnits` returns 0) — the reason HpScreen chose it.
        val usable = 27f - 2 * ROW_SIDE_MARGIN_UNITS
        val labels = 2 * (7f / Layout.DETAIL_CHARS_PER_UNIT)
        val row = labels + 1f + 6 * PIP_TAP_PITCH_UNITS
        assertTrue(row <= usable, "the death-save row fits: $row")
    }

    // ---- buttons and chips -----------------------------------------------------------------------------------

    @Test
    fun aPadButtonClearsItsLabelAndOutgrowsARow() {
        val buttonLine = Layout.lineBoxUnits(30f, 1.10f)
        assertClose(1.71f, buttonLine, "the Button line box, 30 × 1.10")
        assertTrue(PAD_BUTTON_HEIGHT_UNITS > buttonLine, "the label fits")
        assertTrue(PAD_BUTTON_HEIGHT_UNITS > ROW_HEIGHT_GRID_UNITS, "and is a bigger target than a list row")
    }

    @Test
    fun aChipsGlyphFitsItsRowBesideTheLabel() {
        assertTrue(CHIP_ICON_SIZE_UNITS < ROW_HEIGHT_GRID_UNITS, "the glyph clears the row")
        // LightIcon defaults to 2 units square (LightIcon.kt:20), which would leave a quarter unit.
        assertTrue(CHIP_ICON_SIZE_UNITS < 2f, "smaller than the SDK's default, which crowds a 2.5-unit row")
    }

    // ---- the content area ------------------------------------------------------------------------------------

    @Test
    fun theContentAreaIsTheGridMinusBothBars() {
        assertEquals(23f, CONTENT_HEIGHT_GRID_UNITS, "31 − 3 top bar − 4 bottom bar − 1 bottom-bar margin")
        assertEquals(31, Layout.GRID_HEIGHT_UNITS, "LightGrid.HEIGHT")
        assertEquals(600f, Layout.TYPE_SCALE_BASELINE_PX, "designVerticalPxToSp's baseline")
    }
}
