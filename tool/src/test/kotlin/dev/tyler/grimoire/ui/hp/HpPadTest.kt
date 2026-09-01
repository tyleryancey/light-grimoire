package dev.tyler.grimoire.ui.hp

import dev.tyler.grimoire.Fixtures
import dev.tyler.grimoire.rules.Character
import dev.tyler.grimoire.rules.Event
import dev.tyler.grimoire.rules.Ledger
import dev.tyler.grimoire.rules.Model
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** S3's verb × sign table (docs/UI-SPEC.md S3), exhaustively — every verb against every sign the pad draws. */
class HpPadTest {
    private val paladin: Character by lazy { Model.decode(Fixtures.character("paladin-6-warlock-2")) }

    /** The magnitudes the pad draws, both ways, plus the wheel's ±1. */
    private val signed = listOf(-10, -5, -1, 1, 5, 10)

    @Test
    fun damageMinusTakesHitPoints() {
        assertEquals(Event.Damage(5), eventFor(Verb.DAMAGE, -5), "DAMAGE −5 takes 5")
    }

    @Test
    fun damagePlusGivesItBack() {
        assertEquals(Event.Heal(5), eventFor(Verb.DAMAGE, 5), "DAMAGE +5 gives 5 back, through heal()")
    }

    @Test
    fun healPlusHeals() {
        assertEquals(Event.Heal(5), eventFor(Verb.HEAL, 5), "HEAL +5 heals 5")
    }

    @Test
    fun healMinusDamages() {
        assertEquals(Event.Damage(5), eventFor(Verb.HEAL, -5), "HEAL −5 damages 5, through damage()")
    }

    @Test
    fun tempPlusIsAGrantThatKeepsTheHigherNumber() {
        assertEquals(Event.Temp(5), eventFor(Verb.TEMP, 5), "TEMP +5 is a grant, not a correction")
    }

    @Test
    fun tempMinusIsTheSignedCorrection() {
        assertEquals(Event.TempDelta(-5), eventFor(Verb.TEMP, -5), "TEMP −5 is the only way temp HP comes down")
    }

    @Test
    fun everyVerbAndEverySignMapsToItsOwnEngineFunction() {
        for (delta in signed) {
            val magnitude = if (delta < 0) -delta else delta
            for (verb in listOf(Verb.DAMAGE, Verb.HEAL)) {
                val expected = if (delta < 0) Event.Damage(magnitude) else Event.Heal(magnitude)
                assertEquals(expected, eventFor(verb, delta), "$verb $delta")
            }
            val expectedTemp = if (delta < 0) Event.TempDelta(delta) else Event.Temp(magnitude)
            assertEquals(expectedTemp, eventFor(Verb.TEMP, delta), "TEMP $delta")
        }
    }

    @Test
    fun damageAndHealAreOneSignedMapping() {
        // Not an accident of the implementation — the UI-SPEC's own table gives the two chips the same
        // pair of functions. They differ only in which row of the pad the player reaches for first, which
        // is why the chip can be switched mid-fight without changing what a button does.
        for (delta in signed) {
            assertEquals(eventFor(Verb.DAMAGE, delta), eventFor(Verb.HEAL, delta), "DAMAGE and HEAL at $delta")
        }
    }

    @Test
    fun zeroCountsAsPositive() {
        assertEquals(Event.Heal(0), eventFor(Verb.DAMAGE, 0), "a zero the pad never produces is still a no-op, not a negative amount")
        assertEquals(Event.Temp(0), eventFor(Verb.TEMP, 0), "and a grant of nothing rather than a correction of nothing")
    }

    @Test
    fun theExtremeNegativeDoesNotWrapBackToItself() {
        // Negating Int.MIN_VALUE in place stays negative, and a negative amount throws out of Ledger.damage
        // on the screen a player is mid-combat on.
        assertEquals(Event.Damage(Int.MAX_VALUE), eventFor(Verb.DAMAGE, Int.MIN_VALUE), "clamped, not wrapped")
    }

    @Test
    fun thePipValuesStraddleTheThresholdWithoutTouchingEitherNatural() {
        assertTrue(PIP_SUCCESS_D20 !in listOf(1, 20) && PIP_FAILURE_D20 !in listOf(1, 20), "a pip tap can never be a natural")
        val success = Ledger.deathSave(paladin, PIP_SUCCESS_D20)
        assertEquals(paladin.deathSaves.successes + 1, success.deathSaves.successes, "$PIP_SUCCESS_D20 is a success")
        assertEquals(paladin.hp, success.hp, "and it regains no hit points, unlike a natural 20")
        val failure = Ledger.deathSave(paladin, PIP_FAILURE_D20)
        assertEquals(paladin.deathSaves.failures + 1, failure.deathSaves.failures, "$PIP_FAILURE_D20 is one failure")
    }

    @Test
    fun eachStripIsThreePipsLong() {
        assertEquals(3, DEATH_SAVE_PIPS, "three successes stabilise and three failures kill")
    }
}
