package dev.tyler.grimoire.ui.hp

import dev.tyler.grimoire.Fixtures
import dev.tyler.grimoire.rules.Character
import dev.tyler.grimoire.rules.Event
import dev.tyler.grimoire.rules.Ledger
import dev.tyler.grimoire.rules.Model
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What S3's status line actually reads, field by field (docs/UI-SPEC.md S3).
 *
 * `HpViewModelTest` drives the actions; this drives the *rendering* — the two derived strings and the one
 * boolean that reach the screen and nothing else. They are worth their own suite because the composable
 * around them cannot be exercised from this gate: a swap of [HpUiState.bloodied] for `down`, or of the
 * badge and the temp clause, moves nothing any action-level test can see.
 */
class HpUiStateTest {
    private val cleric: Character by lazy { Model.decode(Fixtures.character("cleric-5-life")) }

    /** The paladin fixture is the DYING start: 0 hit points, one success and one failure recorded. */
    private val paladin: Character by lazy { Model.decode(Fixtures.character("paladin-6-warlock-2")) }

    /** Two more successes on top of the paladin's one: stable, still at 0 HP. */
    private val stable: Character by lazy {
        Ledger.run(paladin, listOf(Event.DeathSave(15), Event.DeathSave(15)))
    }

    /** Two more failures: dead, still at 0 HP. */
    private val dead: Character by lazy {
        Ledger.run(paladin, listOf(Event.DeathSave(3), Event.DeathSave(3)))
    }

    private fun state(c: Character, temp: Int = 0): HpUiState =
        HpUiState.of(c.copy(hp = c.hp.copy(temp = temp)), Verb.DAMAGE)

    // ---- the status line's order -----------------------------------------------------------------------------

    @Test
    fun theBadgeIsTheLastThingOnTheLineAndTempComesBeforeIt() {
        // The spec's own example, `0 / 43 · temp 6   DOWN` (its 43 is the cleric's maximum; the paladin
        // fixture is the one that starts at 0, with 68): temp adjacent to the numbers carrying its own
        // separator, the badge trailing after a gap. Every S3 frame puts the badge last.
        val s = state(paladin, temp = 6)
        assertEquals("0 / 68", s.numbers, "the numbers")
        assertEquals(" · temp 6", s.statusTemp, "temp sits immediately after them, separator and all")
        assertEquals("DOWN", s.statusTrailing, "and the badge trails, after the gap the screen inserts")
    }

    @Test
    fun theWireframesWithoutTempAreJustTheZeroCaseOfTheSameLine() {
        assertEquals("", state(paladin).statusTemp, "no temporary hit points, no clause — `0 / 68   DOWN`")
        assertEquals("DOWN", state(paladin).statusTrailing, "the badge is unmoved by temp being absent")
        assertEquals("", state(stable).statusTemp, "`0 / 68   STABLE` likewise")
        assertEquals("STABLE", state(stable).statusTrailing, "its own word")
    }

    @Test
    fun stableTrailsTempExactlyAsDyingDoes() {
        // Temp HP absorb damage at 0 whichever of the two states the character is in, so neither hides them.
        val s = state(stable, temp = 6)
        assertEquals(" · temp 6", s.statusTemp, "the same clause")
        assertEquals("STABLE", s.statusTrailing, "under the other badge")
    }

    @Test
    fun upDrawsTempOnItsOwnLineSoTheStatusLineCarriesNeitherHalf() {
        val s = state(cleric, temp = 6)
        assertEquals(HpMode.UP, s.mode, "31 of 43")
        assertEquals("", s.statusTemp, "UP has a `temp n` line of its own — see the wireframe")
        assertEquals("", s.statusTrailing, "and no badge at all")
        assertEquals(6, s.temp, "the number is still there for that line to draw")
    }

    @Test
    fun deadCarriesNeitherHalfEvenWithTemporaryHitPointsOnTheSheet() {
        val s = state(dead, temp = 6)
        assertEquals(HpMode.DEAD, s.mode, "three failures")
        assertEquals("", s.statusTemp, "DEAD draws its badge above the numbers, so the line trails nothing")
        assertEquals("", s.statusTrailing, "including the badge itself, which is not on this line")
        assertEquals("DEAD", s.badge, "the word the block above the numbers uses")
    }

    @Test
    fun everyModeAgreesWithItsBadge() {
        // statusTrailing is the badge or it is empty; it is never some third string.
        for (c in listOf(cleric, paladin, stable, dead)) {
            val s = state(c)
            assertTrue(
                s.statusTrailing == s.badge || s.statusTrailing.isEmpty(),
                "${s.mode} trails '${s.statusTrailing}', which is neither its badge '${s.badge}' nor nothing",
            )
        }
    }

    // ---- bloodied --------------------------------------------------------------------------------------------

    @Test
    fun bloodiedIsAtOrBelowHalfTheMaximum() {
        val max = 40
        fun at(current: Int) = HpUiState.of(
            cleric.copy(hp = cleric.hp.copy(max = max, damage = max - current)),
            Verb.DAMAGE,
        )
        assertTrue(at(20).bloodied, "exactly half is bloodied — the boundary `current * 2 <= max` includes")
        assertFalse(at(21).bloodied, "one above half is not")
        assertTrue(at(1).bloodied, "and the last hit point certainly is")
    }

    @Test
    fun aCharacterAtZeroIsNotBloodiedItIsDown() {
        // The clause that separates the two: `current > 0`. Swapping `bloodied` for `down` on the screen
        // would bold the numbers of a downed character and pass every action-level test in the suite.
        val s = state(paladin)
        assertEquals(0, s.current, "the paladin is at 0")
        assertFalse(s.bloodied, "0 HP is not 'at or below half' — DOWN is the cue, not bold numbers")
        assertEquals(HpMode.DYING, s.mode, "which is the state that says so")
        assertFalse(state(stable).bloodied, "nor is a stable character at 0")
        assertFalse(state(dead).bloodied, "nor a dead one")
    }

    @Test
    fun aHealthyCharacterIsNotBloodied() {
        assertFalse(state(cleric).bloodied, "31 of 43 is well above half")
    }
}
