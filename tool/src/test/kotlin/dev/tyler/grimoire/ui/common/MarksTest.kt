package dev.tyler.grimoire.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `pipStates` — the pure half of every pip strip the tool draws. The composable cannot be exercised
 * from this gate, so what is pinned is which pips it is told to fill.
 */
class MarksTest {
    private fun render(states: List<Boolean>) = states.joinToString("") { if (it) "●" else "○" }

    // ---- FILL ------------------------------------------------------------------------------------------------

    @Test
    fun fillLightsTheFirstValuePips() {
        assertEquals("●●○○", render(pipStates(2, 4, PipStyle.FILL)), "two of four slots left")
        assertEquals("○○○", render(pipStates(0, 3, PipStyle.FILL)), "everything spent")
        assertEquals("●●●", render(pipStates(3, 3, PipStyle.FILL)), "nothing spent")
    }

    @Test
    fun fillDrawsS3sDeathSaveRows() {
        // The DYING wireframe: no successes yet, one failure taken.
        assertEquals("○○○", render(pipStates(0, 3, PipStyle.FILL)), "success ○ ○ ○")
        assertEquals("●○○", render(pipStates(1, 3, PipStyle.FILL)), "failure ●○○")
    }

    @Test
    fun fillSaturatesRatherThanOverflowing() {
        assertEquals("●●●", render(pipStates(9, 3, PipStyle.FILL)), "a value past the maximum fills the strip")
        assertEquals("○○○", render(pipStates(-2, 3, PipStyle.FILL)), "and a negative one fills none")
    }

    // ---- LEVEL -----------------------------------------------------------------------------------------------

    @Test
    fun levelMarksWhereTheCharacterIsNotHowMuchIsLeft() {
        // S7's exhaustion indicator: six pips, level 1 lights the second.
        assertEquals("○●○○○○", render(pipStates(1, 6, PipStyle.LEVEL)), "Exhaustion ○●○○○○ 1")
        assertEquals("●○○○○○", render(pipStates(0, 6, PipStyle.LEVEL)), "no exhaustion")
        assertEquals("○○○○○●", render(pipStates(5, 6, PipStyle.LEVEL)), "the last step on the scale")
    }

    @Test
    fun aLevelOffTheScaleLightsNothingRatherThanThrowing() {
        assertEquals("○○○○○○", render(pipStates(6, 6, PipStyle.LEVEL)), "past the end")
        assertEquals("○○○○○○", render(pipStates(-1, 6, PipStyle.LEVEL)), "before the start")
    }

    @Test
    fun exactlyOnePipIsEverLitOnALevelScale() {
        for (level in -1..7) {
            val lit = pipStates(level, 6, PipStyle.LEVEL).count { it }
            assertTrue(lit <= 1, "level $level lights at most one pip, not $lit")
        }
    }

    // ---- the strip itself ------------------------------------------------------------------------------------

    @Test
    fun theStripIsAlwaysTotalPipsLong() {
        for (total in 0..8) {
            assertEquals(total, pipStates(3, total, PipStyle.FILL).size, "a $total-pip FILL strip")
            assertEquals(total, pipStates(3, total, PipStyle.LEVEL).size, "a $total-pip LEVEL strip")
        }
    }

    @Test
    fun aNegativeTotalIsAnEmptyStripNotACrash() {
        assertEquals(emptyList<Boolean>(), pipStates(2, -3, PipStyle.FILL), "nothing to draw")
        assertEquals(emptyList<Boolean>(), pipStates(2, -3, PipStyle.LEVEL), "nor on a scale")
    }
}
