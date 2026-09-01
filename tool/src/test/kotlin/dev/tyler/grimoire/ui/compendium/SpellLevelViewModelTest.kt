package dev.tyler.grimoire.ui.compendium

import dev.tyler.grimoire.compendium.FakeCompendiumDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * S13.2's level stepper over the real bundle (docs/UI-SPEC.md S13.2). The per-level counts below are the
 * sha256-pinned assets — 24, 49, 54, 42, 31, 37, 31, 20, 16, 15, summing to the hub's 319.
 *
 * The wheel and the arrows are driven through the same view model the screen holds: the scope the loads run on
 * is the constructor's test seam, `Dispatchers.Unconfined` here, because `viewModelScope` dispatches on
 * `Dispatchers.Main`, which does not exist off-device. Unconfined runs each load inline, so `handleKey` has
 * finished its work by the time it returns.
 */
class SpellLevelViewModelTest {
    private companion object {
        /** Spells per level 0..9 in the bundle. */
        val COUNTS = listOf(24, 49, 54, 42, 31, 37, 31, 20, 16, 15)
    }

    private fun vm(dao: FakeCompendiumDao = Bundle.dao(), driven: Boolean = false) =
        SpellLevelViewModel(Bundle.reader(dao), if (driven) CoroutineScope(Dispatchers.Unconfined) else null)

    private fun FakeCompendiumDao.queries() = calls.count { it == "spellsByLevel" }

    // ---- loading ---------------------------------------------------------------------------------------------

    @Test
    fun opensOnTheCantrips() = runBlocking {
        val model = vm()
        assertTrue(model.state.value.loading, "the screen starts loading")
        assertEquals(0, model.state.value.level, "on level 0")
        assertEquals("CANTRIPS", model.state.value.subtitle, "which the bar calls CANTRIPS, not LEVEL 0")
        model.show(model.state.value.level)
        val state = model.state.value
        assertFalse(state.loading, "the wait ends with the rows")
        assertEquals(24, state.spells.size, "the bundle's 24 cantrips")
        assertEquals("CANTRIPS · 24", state.stepper, "the stepper row names the level and its count")
        assertEquals("Acid Splash", state.spells.first().name, "in name order")
    }

    @Test
    fun everyLevelLoadsItsOwnSpells() = runBlocking {
        val dao = Bundle.dao()
        val model = vm(dao)
        for (level in 0..9) {
            model.show(level)
            assertEquals(level, model.state.value.level, "the state follows the level asked for")
            assertEquals(COUNTS[level], model.state.value.spells.size, "level $level's count comes from the bundle")
            assertTrue(model.state.value.spells.all { it.level == level }, "and every row is of that level")
        }
        assertEquals(319, COUNTS.sum(), "which sums to the SPELLS count on the hub")
        assertEquals(10, dao.queries(), "one query per level, none wasted")
    }

    @Test
    fun steppingUpWalksTheLevelsInOrder() {
        val model = vm(driven = true)
        for (level in 1..9) {
            model.levelUp()
            assertEquals(level, model.state.value.level, "one step up from ${level - 1}")
            assertEquals(COUNTS[level], model.state.value.spells.size, "level $level's spells arrive with it")
        }
        model.levelDown()
        assertEquals(8, model.state.value.level, "and a step down comes back")
        assertEquals(COUNTS[8], model.state.value.spells.size, "with the level below's spells")
    }

    @Test
    fun theSubtitleNamesTheLevel() = runBlocking {
        val model = vm()
        model.show(0)
        assertEquals("CANTRIPS", model.state.value.subtitle, "level 0 is the cantrips")
        model.show(3)
        assertEquals("LEVEL 3", model.state.value.subtitle, "every other level reads as itself")
        assertEquals("LEVEL 3 · 42", model.state.value.stepper, "and the stepper adds the count")
        model.show(9)
        assertEquals("LEVEL 9", model.state.value.subtitle, "up to the ninth")
    }

    // ---- the clamps ------------------------------------------------------------------------------------------

    @Test
    fun thereIsNothingBelowTheCantrips() {
        val dao = Bundle.dao()
        val model = vm(dao, driven = true)
        model.levelUp()
        model.levelDown()
        val queriesAtZero = dao.queries()
        assertEquals(0, model.state.value.level, "back on the cantrips")
        assertFalse(model.state.value.hasLower, "and the arrow below is drawn as unavailable")
        model.levelDown()
        assertTrue(model.handleKey(318), "the wheel is still consumed at the end of the range")
        assertEquals(0, model.state.value.level, "the clamp does not wrap to level 9")
        assertEquals(queriesAtZero, dao.queries(), "and re-queries nothing past the end")
    }

    @Test
    fun thereIsNothingAboveTheNinth() = runBlocking {
        val dao = Bundle.dao()
        val model = vm(dao, driven = true)
        model.show(9)
        val queriesAtNine = dao.queries()
        assertFalse(model.state.value.hasHigher, "the arrow above is drawn as unavailable")
        model.levelUp()
        assertTrue(model.handleKey(317), "the wheel is still consumed")
        assertEquals(9, model.state.value.level, "the clamp does not wrap to the cantrips")
        assertEquals(15, model.state.value.spells.size, "the ninth-level spells stay on screen")
        assertEquals(queriesAtNine, dao.queries(), "and nothing is re-queried")
    }

    @Test
    fun aSecondShowOfTheSameLevelDoesNotReload() = runBlocking {
        val dao = Bundle.dao()
        val model = vm(dao)
        model.show(0)
        val first = model.state.value
        model.show(0)
        assertEquals(1, dao.queries(), "a relaunch's second show runs no query")
        assertEquals(first, model.state.value, "and cannot clobber what is already on screen")
    }

    // ---- keys ------------------------------------------------------------------------------------------------

    @Test
    fun theWheelAndTheArrowsAreTheSameStep() {
        val byWheel = vm(driven = true)
        assertTrue(byWheel.handleKey(317), "a turn toward the top of the phone is consumed")
        val byArrow = vm(driven = true)
        byArrow.levelUp()
        assertEquals(byArrow.state.value, byWheel.state.value, "317 and the ▸ arrow do the same thing")
        assertEquals(1, byWheel.state.value.level, "which is a step up, per S13.2's wheel table")

        val downWheel = vm(driven = true)
        assertTrue(downWheel.handleKey(318), "a turn toward the bottom is consumed")
        downWheel.levelUp()
        assertTrue(downWheel.handleKey(318), "and steps the level down again")
        assertEquals(0, downWheel.state.value.level, "318 lowers the level, the mirror of 317")
    }

    @Test
    fun thePressIsConsumedAsANoOp() {
        val dao = Bundle.dao()
        val model = vm(dao, driven = true)
        model.levelUp()
        val before = model.state.value
        val queries = dao.queries()
        assertTrue(model.handleKey(319), "the press is consumed so LightOS never relaunches the tool")
        assertEquals(before, model.state.value, "S13.2 has no primary action")
        assertEquals(queries, dao.queries(), "and the press runs no query")
    }

    @Test
    fun nonWheelKeysAreLeftToLightOs() {
        val model = vm(driven = true)
        assertFalse(model.handleKey(24), "volume up stays unconsumed")
        assertFalse(model.handleKey(25), "volume down stays unconsumed")
        assertFalse(model.handleKey(80), "camera focus stays unconsumed")
        assertFalse(model.handleKey(4), "back stays unconsumed")
    }

    @Test
    fun theReleaseHalfOfEveryDetentIsSwallowedToo() {
        val dao = Bundle.dao()
        val model = vm(dao, driven = true)
        // One detent is a DOWN/UP pair. `onKeyUp` defaults to false in the SDK's LightKeyHandler and
        // LightActivity forwards any unconsumed key it recognizes to the server with componentToRelaunch set,
        // so a screen that consumed only the DOWN half would still be relaunched by every turn.
        assertTrue(model.consumesKey(317), "the release of a turn toward the top is consumed")
        assertTrue(model.consumesKey(318), "and of a turn toward the bottom")
        assertTrue(model.consumesKey(319), "and of the press")
        assertFalse(model.consumesKey(24), "volume up is still LightOS's")
        assertFalse(model.consumesKey(25), "so is volume down")
        assertFalse(model.consumesKey(80), "so is camera focus")
        assertFalse(model.consumesKey(4), "and so is back")
        assertEquals(0, model.state.value.level, "and none of it stepped the level")
        assertEquals(0, dao.queries(), "swallowing a release runs no query at all")
    }

    @Test
    fun consumingAndActingAgreeOnEveryKey() {
        // Two reads of WheelHandler.of that could drift apart: whatever handleKey acts on, consumesKey swallows.
        for (keyCode in listOf(317, 318, 319, 24, 25, 80, 4)) {
            val model = vm(driven = true)
            assertEquals(model.handleKey(keyCode), model.consumesKey(keyCode), "key $keyCode is judged once")
        }
    }
}
