package dev.tyler.grimoire.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `Signs.mod` and `padMagnitudes` — the pure halves of the stat header and the number pad. Every
 * signed number the tool prints comes through the first of them, so its glyph is pinned by code
 * point, not by eye: a hyphen-minus and a U+2212 look nearly the same in a diff and nothing alike
 * in a column of modifiers.
 */
class SignsTest {
    private val MINUS = '−'

    // ---- Signs.mod ------------------------------------------------------------------------------------------

    @Test
    fun aModifierIsAlwaysSigned() {
        assertEquals("+3", Signs.mod(3), "a positive modifier")
        assertEquals("+0", Signs.mod(0), "zero is written +0, not 0 — a sheet's columns are all signed")
        assertEquals("${MINUS}1", Signs.mod(-1), "a negative one")
        assertEquals("+5", Signs.mod(5), "Brother Aldric's WIS save")
        assertEquals("${MINUS}12", Signs.mod(-12), "two digits")
    }

    @Test
    fun theMinusIsTheTypographicOneNotAHyphen() {
        val negative = Signs.mod(-4)
        assertEquals('−', negative[0], "U+2212 MINUS SIGN, which lines up with the + of the same run")
        assertTrue('-' !in negative, "never a hyphen-minus: $negative")
    }

    @Test
    fun everyModifierAScoreCanProduceFormats() {
        // Ability scores run 1..30, so modifiers run −5..+10 (floorDiv((score − 10) / 2)).
        for (modifier in -5..10) {
            val text = Signs.mod(modifier)
            assertTrue(text.length in 2..3, "$modifier formats short: $text")
            assertTrue(text[0] == '+' || text[0] == MINUS, "$modifier is signed: $text")
        }
    }

    @Test
    fun theExtremeIsFormattedRatherThanOverflowed() {
        // Nothing in the model reaches it, but negating Int.MIN_VALUE in place would wrap to itself.
        assertEquals("${MINUS}2147483648", Signs.mod(Int.MIN_VALUE), "the minimum still reads as a negative")
    }

    // ---- the pad's labels -----------------------------------------------------------------------------------

    @Test
    fun theNumberPadIsLabelledByTheSameFormatter() {
        val magnitudes = padMagnitudes(DEFAULT_PAD_DELTAS)
        assertEquals(listOf("+10", "+5", "+1"), magnitudes.map { Signs.mod(it) }, "the pad's lower row")
        assertEquals(
            listOf("${MINUS}10", "${MINUS}5", "${MINUS}1"),
            magnitudes.map { Signs.mod(-it) },
            "and its upper one, mirrored",
        )
    }

    // ---- padMagnitudes --------------------------------------------------------------------------------------

    @Test
    fun theDefaultPadIsTheWireframesThreeSteps() {
        assertEquals(listOf(10, 5, 1), padMagnitudes(DEFAULT_PAD_DELTAS), "−10 −5 −1 over +10 +5 +1")
    }

    @Test
    fun theCallerSuppliesMagnitudesAndThePadSuppliesTheSigns() {
        assertEquals(listOf(10, 5, 1), padMagnitudes(listOf(-10, 5, -1)), "signs are the pad's job, not the caller's")
    }

    @Test
    fun aDegenerateButtonIsDroppedWhichNarrowsThePad() {
        // Both rows are built from this one list, so they cannot disagree with each other; what a
        // dropped entry costs is a *narrower* pad than the three equal-weight buttons S3's budget
        // was derived for. Dropping is still the right answer — a zero button would do nothing and
        // a repeated one would give the pad two identical keys — but the caller should pass three.
        assertEquals(listOf(5, 1), padMagnitudes(listOf(5, 5, 1)), "a repeat collapses to one button, not two")
        assertEquals(listOf(1), padMagnitudes(listOf(0, 1)), "and a zero is dropped outright")
        assertEquals(emptyList<Int>(), padMagnitudes(emptyList()), "an empty pad draws nothing")
        assertEquals(3, padMagnitudes(DEFAULT_PAD_DELTAS).size, "the default pad keeps all three buttons")
    }

    @Test
    fun everyMagnitudeIsPositiveHoweverTheCallerWroteIt() {
        val magnitudes = padMagnitudes(listOf(20, -5, 5, 0, 1))
        assertEquals(listOf(20, 5, 1), magnitudes, "order kept, sign and duplicate removed")
        assertTrue(magnitudes.all { it > 0 }, "so the `−n` row is exactly the `+n` row negated: $magnitudes")
    }
}
